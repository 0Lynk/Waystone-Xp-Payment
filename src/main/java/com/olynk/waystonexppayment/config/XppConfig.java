package com.olynk.waystonexppayment.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config spec. Values are never read directly by gameplay code: {@link XppSettings} keeps a
 * snapshot so that code running before the config is loaded still sees sane defaults.
 */
public final class XppConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue TANK_CAPACITY_MB;
    public static final ModConfigSpec.BooleanValue ALLOW_BUCKET_INTERACTION;
    public static final ModConfigSpec.BooleanValue REMOTE_DESTINATION_PAYMENTS;
    public static final ModConfigSpec.IntValue REMOTE_PAYMENT_CHUNK_LOADS;
    public static final ModConfigSpec.BooleanValue SKIP_PAYMENT_IN_CREATIVE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Payment rules").push("payment");

        // How much a fluid is worth is a datapack question now: a rate read from a config file could disagree
        // with the pack the server actually runs, and the pack is the only place that can know modded fluids.
        SKIP_PAYMENT_IN_CREATIVE = builder
                .comment("When true, players in creative mode are never charged (neither tank nor XP).")
                .define("skipPaymentInCreative", true);

        builder.pop();

        builder.comment("Payment tank device").push("tank");

        TANK_CAPACITY_MB = builder
                .comment("Internal tank capacity in mB. 16000 mB = 16 buckets.",
                        "The ceiling exists because a tank is a wallet, not a storage block: at 10 XP/bucket a",
                        "16-bucket tank already covers every fare Waystones can charge by default (max 27 XP).")
                .defineInRange("capacityMb", 16000, 1000, 16000);

        ALLOW_BUCKET_INTERACTION = builder
                .comment("Whether right-clicking the tank with a bucket fills/drains it.",
                        "Disable to force pipe-only interaction.")
                .define("allowBucketInteraction", true);

        REMOTE_DESTINATION_PAYMENTS = builder
                .comment("When true, the pool below the *destination* waystone can pay even while its chunk is",
                        "not loaded: the server loads that one chunk (without generating it) to read the tank.",
                        "Set false to require the destination chunk to be loaded already, which is what the mod",
                        "did before 0.2.1 and leaves far-away destinations payable by the player only.")
                .define("remoteDestinationPayments", true);

        REMOTE_PAYMENT_CHUNK_LOADS = builder
                .comment("How many chunk loads a single teleport may spend looking for that destination pool.",
                        "0 = never load, 1 = the default (the teleport loads the chunk microseconds later anyway).",
                        "Each load is a synchronous read on the server thread, so this stays small on purpose.")
                .defineInRange("remotePaymentChunkLoadsPerTeleport", 1, 0, 4);

        builder.pop();

        SPEC = builder.build();
    }

    private XppConfig() {
    }
}
