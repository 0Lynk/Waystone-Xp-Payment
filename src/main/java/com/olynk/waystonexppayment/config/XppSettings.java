package com.olynk.waystonexppayment.config;

/**
 * Immutable snapshot of the COMMON config, held in a volatile so it is readable at any time.
 * {@link #DEFAULTS} is used before the config file has been loaded.
 */
public record XppSettings(int mbPerXp, int capacityMb, boolean allowBucketInteraction,
                          boolean skipPaymentInCreative, boolean acceptUndeclaredFluids) {

    public static final XppSettings DEFAULTS = new XppSettings(1, 4000, true, true, false);

    private static volatile XppSettings current = DEFAULTS;

    public static XppSettings current() {
        return current;
    }

    /** Re-reads the config spec. Falls back to {@link #DEFAULTS} when the config is not loaded yet. */
    public static void reloadFromConfig() {
        try {
            current = new XppSettings(
                    XppConfig.MB_PER_XP.get(),
                    XppConfig.TANK_CAPACITY_MB.get(),
                    XppConfig.ALLOW_BUCKET_INTERACTION.get(),
                    XppConfig.SKIP_PAYMENT_IN_CREATIVE.get(),
                    XppConfig.ACCEPT_UNDECLARED_FLUIDS.get());
        } catch (IllegalStateException e) {
            current = DEFAULTS;
        }
    }
}
