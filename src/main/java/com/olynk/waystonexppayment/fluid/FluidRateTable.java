package com.olynk.waystonexppayment.fluid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure (Minecraft-free) lookup table: which fluid (by id or by tag) counts as "experience fluid", and how many
 * milli-buckets (mB) of it are worth 1 XP.
 *
 * <p>Lookup order (design ADR-10): exact fluid id -> fluid tag -> fallback (only if undeclared fluids are accepted).
 * A fluid that is not declared is <em>not</em> accepted, which keeps the tank single-purpose and predictable.
 *
 * <p>Default rate is {@value #DEFAULT_MB_PER_XP} mB per XP, matching Create: Enchantment Industry's own
 * experience fluid (authoritative reference: {@code ExperienceHelper#getExperienceFromFluid} returns the mB amount
 * verbatim for {@code create_enchantment_industry:experience}).
 */
public final class FluidRateTable {

    public static final int DEFAULT_MB_PER_XP = 1;

    private final int fallbackMbPerXp;
    private final boolean acceptUndeclared;
    private final boolean acceptExperienceNamedFluids;
    private final Map<String, Integer> byFluidId;
    private final Map<String, Integer> byTag;

    /** A resolved rate plus why it was chosen, so logs and the info command can explain themselves. */
    public record Resolution(int mbPerXp, String source) {
    }

    private FluidRateTable(int fallbackMbPerXp, boolean acceptUndeclared, boolean acceptExperienceNamedFluids,
                           Map<String, Integer> byFluidId, Map<String, Integer> byTag) {
        this.fallbackMbPerXp = fallbackMbPerXp;
        this.acceptUndeclared = acceptUndeclared;
        this.acceptExperienceNamedFluids = acceptExperienceNamedFluids;
        this.byFluidId = Map.copyOf(byFluidId);
        this.byTag = Map.copyOf(byTag);
    }

    /**
     * Resolves a rate and records how it was found.
     *
     * <p>Order: exact fluid id -> the id's still/flowing counterpart -> fluid tag -> experience-sounding name
     * (opt-in) -> fallback (opt-in). The counterpart step exists because mods register one id per variant
     * ({@code x:xp_fluid} and {@code x:flowing_xp_fluid}) and a tank can end up holding either one depending on
     * whether it was filled by pipe or by bucket.
     *
     * @param fluidId fluid id in {@code namespace:path} form
     * @param tags    the fluid's tag ids in {@code namespace:path} form (no leading {@code #})
     */
    public Optional<Resolution> resolve(String fluidId, Collection<String> tags) {
        Integer exact = byFluidId.get(fluidId);
        if (exact != null) {
            return Optional.of(new Resolution(exact, "exact id " + fluidId));
        }

        String counterpart = counterpartId(fluidId);
        if (counterpart != null) {
            Integer mirrored = byFluidId.get(counterpart);
            if (mirrored != null) {
                return Optional.of(new Resolution(mirrored, "counterpart id " + counterpart));
            }
        }

        for (String tag : tags) {
            Integer byTagRate = byTag.get(tag);
            if (byTagRate != null) {
                return Optional.of(new Resolution(byTagRate, "tag #" + tag));
            }
        }

        if (acceptExperienceNamedFluids && looksLikeExperience(fluidId)) {
            return Optional.of(new Resolution(fallbackMbPerXp, "experience-like name"));
        }

        return acceptUndeclared ? Optional.of(new Resolution(fallbackMbPerXp, "fallback (undeclared accepted)"))
                : Optional.empty();
    }

    /**
     * @param fluidId fluid id in {@code namespace:path} form
     * @param tags    the fluid's tag ids in {@code namespace:path} form (no leading {@code #})
     */
    public OptionalInt rateFor(String fluidId, Collection<String> tags) {
        return resolve(fluidId, tags).map(resolution -> OptionalInt.of(resolution.mbPerXp()))
                .orElseGet(OptionalInt::empty);
    }

    /** The same fluid id with {@code flowing_} added to, or removed from, its path. */
    static String counterpartId(String fluidId) {
        int colon = fluidId.indexOf(':');
        if (colon < 0 || colon == fluidId.length() - 1) {
            return null;
        }
        String namespace = fluidId.substring(0, colon);
        String path = fluidId.substring(colon + 1);
        if (path.startsWith("flowing_")) {
            return namespace + ":" + path.substring("flowing_".length());
        }
        return namespace + ":flowing_" + path;
    }

    /**
     * Whether an id is named after experience rather than declared.
     *
     * <p>Deliberately narrow - a whole {@code xp} or {@code experience} segment, never a substring - so
     * {@code some_mod:xp_fluid}, {@code some_mod:flowing_xp_fluid}, {@code some_mod:xp_still} and
     * {@code some_mod:liquid_experience} qualify while {@code some_mod:expansion} does not.
     */
    static boolean looksLikeExperience(String fluidId) {
        int colon = fluidId.indexOf(':');
        String path = colon < 0 ? fluidId : fluidId.substring(colon + 1);
        for (String part : path.split("_")) {
            if (part.equals("xp") || part.equals("experience")) {
                return true;
            }
        }
        return false;
    }

    public boolean accepts(String fluidId, Collection<String> tags) {
        return rateFor(fluidId, tags).isPresent();
    }

    /** XP value of {@code mb} milli-buckets, floored (remainder stays in the tank). */
    public int xpValue(String fluidId, Collection<String> tags, int mb) {
        if (mb <= 0) {
            return 0;
        }
        OptionalInt rate = rateFor(fluidId, tags);
        return rate.isEmpty() ? 0 : mb / rate.getAsInt();
    }

    public boolean acceptUndeclared() {
        return acceptUndeclared;
    }

    public int fallbackMbPerXp() {
        return fallbackMbPerXp;
    }

    public int declaredCount() {
        return byFluidId.size() + byTag.size();
    }

    /** Declared fluid ids ({@code namespace:path}). */
    public java.util.Set<String> declaredFluidIds() {
        return byFluidId.keySet();
    }

    /** Declared fluid tag ids ({@code namespace:path}, no leading {@code #}). */
    public java.util.Set<String> declaredTags() {
        return byTag.keySet();
    }

    /**
     * Parses one config/recipe entry of the form {@code <fluid id or #tag>=<mbPerXp>}, e.g.
     * {@code create_enchantment_industry:experience=1} or {@code #c:experience=1}.
     *
     * @return the normalized key (tag keys keep their leading {@code #}) and the rate, or empty if malformed
     */
    public static Optional<Map.Entry<String, Integer>> parseEntry(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int eq = trimmed.lastIndexOf('=');
        if (eq <= 0 || eq == trimmed.length() - 1) {
            return Optional.empty();
        }
        String key = trimmed.substring(0, eq).trim();
        String value = trimmed.substring(eq + 1).trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        int rate;
        try {
            rate = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (rate < 1) {
            return Optional.empty();
        }
        return Optional.of(Map.entry(key, rate));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Integer> fluidIds = new LinkedHashMap<>();
        private final Map<String, Integer> tags = new LinkedHashMap<>();
        private int fallbackMbPerXp = DEFAULT_MB_PER_XP;
        private boolean acceptUndeclared;
        private boolean acceptExperienceNamedFluids = true;

        public Builder fallbackMbPerXp(int value) {
            this.fallbackMbPerXp = Math.max(1, value);
            return this;
        }

        public Builder acceptUndeclared(boolean value) {
            this.acceptUndeclared = value;
            return this;
        }

        public Builder acceptExperienceNamedFluids(boolean value) {
            this.acceptExperienceNamedFluids = value;
            return this;
        }

        /**
         * @param key  {@code namespace:path} for a fluid, or {@code #namespace:path} for a fluid tag
         * @param rate mB per 1 XP, or {@code null} to use the fallback rate
         */
        public Builder declare(String key, Integer rate) {
            if (key == null || key.isBlank()) {
                return this;
            }
            String trimmed = key.trim();
            int effective = rate == null ? fallbackMbPerXp : Math.max(1, rate);
            if (trimmed.startsWith("#")) {
                tags.put(trimmed.substring(1), effective);
            } else {
                fluidIds.put(trimmed, effective);
            }
            return this;
        }

        public Builder declareAll(Collection<String> entries) {
            for (String entry : entries) {
                parseEntry(entry).ifPresent(e -> declare(e.getKey(), e.getValue()));
            }
            return this;
        }

        public FluidRateTable build() {
            return new FluidRateTable(fallbackMbPerXp, acceptUndeclared, acceptExperienceNamedFluids, fluidIds, tags);
        }
    }
}
