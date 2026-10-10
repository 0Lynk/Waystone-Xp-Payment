package com.olynk.waystonexppayment.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config spec. Values are never read directly by gameplay code: {@link XppSettings} keeps a
 * snapshot so that code running before the config is loaded still sees sane defaults.
 */
public final class XppConfig {

    /** Fluids accepted as payment out of the box (Create: Enchantment Industry + the common tag). */
    public static final List<String> DEFAULT_FLUID_CONVERSIONS = List.of(
            "create_enchantment_industry:experience=1",
            "#c:experience=1");

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MB_PER_XP;
    public static final ModConfigSpec.IntValue TANK_CAPACITY_MB;
    public static final ModConfigSpec.BooleanValue ALLOW_BUCKET_INTERACTION;
    public static final ModConfigSpec.BooleanValue SKIP_PAYMENT_IN_CREATIVE;
    public static final ModConfigSpec.BooleanValue ACCEPT_UNDECLARED_FLUIDS;
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

        builder.pop();

        SPEC = builder.build();
    }

    private XppConfig() {
    }
}
