package com.olynk.waystonexppayment.config;

/**
 * Immutable snapshot of the COMMON config, held in a volatile so it is readable at any time.
 * {@link #DEFAULTS} is used before the config file has been loaded.
 *
 * <p>No rate lives here: how much a fluid is worth is decided by datapack declarations only, so a config file
 * can never disagree with the pack the server actually runs.
 */
public record XppSettings(int capacityMb, boolean allowBucketInteraction,
                          boolean skipPaymentInCreative, boolean remoteDestinationPayments,
                          int remotePaymentChunkLoads) {

    public static final XppSettings DEFAULTS = new XppSettings(4000, true, true, true, 1);

    private static volatile XppSettings current = DEFAULTS;

    public static XppSettings current() {
        return current;
    }

    /** Re-reads the config spec. Falls back to {@link #DEFAULTS} when the config is not loaded yet. */
    public static void reloadFromConfig() {
        try {
            current = new XppSettings(
                    XppConfig.TANK_CAPACITY_MB.get(),
                    XppConfig.ALLOW_BUCKET_INTERACTION.get(),
                    XppConfig.SKIP_PAYMENT_IN_CREATIVE.get(),
                    XppConfig.REMOTE_DESTINATION_PAYMENTS.get(),
                    XppConfig.REMOTE_PAYMENT_CHUNK_LOADS.get());
        } catch (IllegalStateException e) {
            current = DEFAULTS;
        }
    }
}
