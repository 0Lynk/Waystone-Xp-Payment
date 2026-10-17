package com.olynk.waystonexppayment.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config spec. Values are never read directly by gameplay code: {@link XppSettings} keeps a
 * snapshot so that code running before the config is loaded still sees sane defaults.
 */
public final class XppConfig {

    /**
     * Fluids accepted as payment out of the box: the common {@code c:experience} tag plus the ids of the
     * experience fluids this addon has actually been tested against. Flowing variants are matched through their
     * still id, so they do not need their own entry.
     */
    public static final List<String> DEFAULT_FLUID_CONVERSIONS = List.of(
            "create_enchantment_industry:experience=1",
            "beyonddimensions:xp_fluid=1",
            "sophisticatedcore:xp_still=1",
            "#c:experience=1");

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MB_PER_XP;
    public static final ModConfigSpec.IntValue TANK_CAPACITY_MB;
    public static final ModConfigSpec.BooleanValue ALLOW_BUCKET_INTERACTION;
    public static final ModConfigSpec.BooleanValue REMOTE_DESTINATION_PAYMENTS;
    public static final ModConfigSpec.IntValue REMOTE_PAYMENT_CHUNK_LOADS;
    public static final ModConfigSpec.BooleanValue SKIP_PAYMENT_IN_CREATIVE;
    public static final ModConfigSpec.BooleanValue ACCEPT_UNDECLARED_FLUIDS;
    public static final ModConfigSpec.BooleanValue ACCEPT_EXPERIENCE_NAMED_FLUIDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLUID_CONVERSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Payment rules").push("payment");

        MB_PER_XP = builder
                .comment("Fallback rate for fluids without an explicit entry: mB of fluid per 1 XP.",
                        "Default 1 matches Create: Enchantment Industry (1 mB = 1 XP).")
                .defineInRange("mbPerXp", 1, 1, 100_000);

        SKIP_PAYMENT_IN_CREATIVE = builder
                .comment("When true, players in creative mode are never charged (neither tank nor XP).")
                .define("skipPaymentInCreative", true);

        ACCEPT_UNDECLARED_FLUIDS = builder
                .comment("When true, a fluid that is neither listed in fluidConversions nor declared by a",
                        "recipe is accepted at mbPerXp. Default false: only declared fluids are valid payment.")
                .define("acceptUndeclaredFluids", false);

        ACCEPT_EXPERIENCE_NAMED_FLUIDS = builder
                .comment("When true, a fluid is accepted at mbPerXp when its id contains a whole 'xp' or",
                        "'experience' word (some_mod:xp_fluid, some_mod:flowing_xp_fluid, some_mod:xp_still,",
                        "some_mod:liquid_experience). Safety net for mods that do not join the c:experience tag;",
                        "set false to accept only fluidConversions, recipe declarations and tags.")
                .define("acceptExperienceNamedFluids", true);

        FLUID_CONVERSIONS = builder
                .comment("Fluid conversions, format '<fluid id or #tag>=<mB per XP>'.",
                        "Examples: 'create_enchantment_industry:experience=1', '#c:experience=1'.",
                        "Fluids declared by waystone_xp_payment:xp_fluid recipes are added on top of this list.")
                .defineListAllowEmpty("fluidConversions", DEFAULT_FLUID_CONVERSIONS, () -> "",
                        value -> value instanceof String);

        builder.pop();

        builder.comment("Payment tank device").push("tank");

        TANK_CAPACITY_MB = builder
                .comment("Internal tank capacity in mB. 4000 mB = 4 buckets.")
                .defineInRange("capacityMb", 4000, 1000, 1_000_000);

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
