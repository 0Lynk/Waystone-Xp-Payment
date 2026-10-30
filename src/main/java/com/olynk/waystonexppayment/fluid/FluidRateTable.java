package com.olynk.waystonexppayment.fluid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure (Minecraft-free) lookup table: which fluid counts as experience and how much a bucket of it is worth.
 *
 * <p>Only exact fluid ids are declared; a still/flowing pair resolves to the same rate because mods register one id
 * per variant and a tank can end up holding either depending on whether it was filled by pipe or by bucket.
 *
 * <p>There is deliberately no tag lookup and no "the name sounds like experience" heuristic. Both used to guess,
 * and a guess in a payment path is either a free teleport or a charge the player cannot explain.
 */
public final class FluidRateTable {

    private final Map<String, Resolution> byFluidId;

    /** A resolved rate plus where it came from, so logs and the info command can explain themselves. */
    public record Resolution(XpRate rate, String source) {
    }

    private FluidRateTable(Map<String, Resolution> byFluidId) {
        this.byFluidId = Map.copyOf(byFluidId);
    }

    /** Resolves a rate: exact fluid id first, then its still/flowing counterpart. */
    public Optional<Resolution> resolve(String fluidId) {
        Resolution exact = byFluidId.get(fluidId);
        if (exact != null) {
            return Optional.of(exact);
        }
        String counterpart = counterpartId(fluidId);
        if (counterpart == null) {
            return Optional.empty();
        }
        Resolution mirrored = byFluidId.get(counterpart);
        return mirrored == null ? Optional.empty()
                : Optional.of(new Resolution(mirrored.rate(), "counterpart id " + counterpart));
    }

    public Optional<XpRate> rateFor(String fluidId) {
        return resolve(fluidId).map(Resolution::rate);
    }

    public boolean accepts(String fluidId) {
        return byFluidId.containsKey(fluidId);
    }

    /** XP value of {@code mb} milli-buckets, floored (the remainder stays in the tank). */
    public int xpValue(String fluidId, int mb) {
        return rateFor(fluidId).map(rate -> rate.xpOfMb(mb)).orElse(0);
    }

    public int declaredCount() {
        return byFluidId.size();
    }

    public Set<String> declaredFluidIds() {
        return byFluidId.keySet();
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects declarations in the order they should win: later calls overwrite earlier ones, and
     * {@link #unregister(String)} removes a fluid that an earlier (lower priority) declaration had claimed.
     */
    public static final class Builder {
        private final Map<String, Resolution> fluidIds = new LinkedHashMap<>();

        /**
         * @param source human readable provenance, e.g. {@code builtin: create_enchantment_industry/experience}
         */
        public Builder declare(String fluidId, XpRate rate, String source) {
            if (fluidId == null || fluidId.isBlank() || rate == null) {
                return this;
            }
            fluidIds.put(fluidId.trim(), new Resolution(rate, source));
            return this;
        }

        public Builder unregister(String fluidId) {
            if (fluidId != null) {
                fluidIds.remove(fluidId.trim());
            }
            return this;
        }

        public Builder declareAll(Collection<FluidDeclaration> declarations) {
            for (FluidDeclaration declaration : declarations) {
                declare(declaration.fluidId(), declaration.rate(), declaration.source());
            }
            return this;
        }

        public FluidRateTable build() {
            return new FluidRateTable(fluidIds);
        }
    }
}
