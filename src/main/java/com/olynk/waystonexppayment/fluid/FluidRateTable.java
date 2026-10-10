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
    private final Map<String, Integer> byFluidId;
    private final Map<String, Integer> byTag;

    private FluidRateTable(int fallbackMbPerXp, boolean acceptUndeclared, Map<String, Integer> byFluidId,
                           Map<String, Integer> byTag) {
        this.fallbackMbPerXp = fallbackMbPerXp;
        this.acceptUndeclared = acceptUndeclared;
        this.byFluidId = Map.copyOf(byFluidId);
        this.byTag = Map.copyOf(byTag);
    }

    /**
     * @param fluidId fluid id in {@code namespace:path} form
     * @param tags    the fluid's tag ids in {@code namespace:path} form (no leading {@code #})
     */
    public OptionalInt rateFor(String fluidId, Collection<String> tags) {
        Integer exact = byFluidId.get(fluidId);
        if (exact != null) {
            return OptionalInt.of(exact);
        }
        for (String tag : tags) {
            Integer byTagRate = byTag.get(tag);
            if (byTagRate != null) {
                return OptionalInt.of(byTagRate);
            }
        }
        return acceptUndeclared ? OptionalInt.of(fallbackMbPerXp) : OptionalInt.empty();
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

        public Builder fallbackMbPerXp(int value) {
            this.fallbackMbPerXp = Math.max(1, value);
            return this;
        }

        public Builder acceptUndeclared(boolean value) {
            this.acceptUndeclared = value;
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
            return new FluidRateTable(fallbackMbPerXp, acceptUndeclared, fluidIds, tags);
        }
    }
}
