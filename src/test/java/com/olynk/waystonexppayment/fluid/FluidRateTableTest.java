package com.olynk.waystonexppayment.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Lookup rules: exact id, still/flowing counterpart, and "nothing declared" meaning "not experience". */
class FluidRateTableTest {

    private static final XpRate ONE_XP_PER_MB = XpRate.ofBucket(1000);
    private static final XpRate TWO_MB_PER_XP = XpRate.parse(0.5);

    @Test
    void anExactDeclarationIsFound() {
        FluidRateTable table = FluidRateTable.builder().declare("a:xp", ONE_XP_PER_MB, "test").build();

        assertTrue(table.accepts("a:xp"));
        assertEquals(ONE_XP_PER_MB, table.rateFor("a:xp").orElseThrow());
    }

    @Test
    void aFlowingVariantCostsTheSameAsTheStillOne() {
        FluidRateTable table = FluidRateTable.builder().declare("a:xp", ONE_XP_PER_MB, "test").build();

        assertEquals(ONE_XP_PER_MB, table.rateFor("a:flowing_xp").orElseThrow());
        assertEquals("counterpart id a:xp", table.resolve("a:flowing_xp").orElseThrow().source());
    }

    @Test
    void aStillVariantIsFoundFromTheFlowingDeclaration() {
        FluidRateTable table = FluidRateTable.builder().declare("a:flowing_xp", ONE_XP_PER_MB, "test").build();

        assertEquals(ONE_XP_PER_MB, table.rateFor("a:xp").orElseThrow());
    }

    @Test
    void anUndeclaredFluidIsNotExperience() {
        FluidRateTable table = FluidRateTable.builder().declare("a:xp", ONE_XP_PER_MB, "test").build();

        assertFalse(table.accepts("a:other"));
        assertTrue(table.rateFor("a:other").isEmpty());
        assertEquals(0, table.xpValue("a:other", 1000));
    }

    @Test
    void theLastDeclarationWins() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("a:xp", ONE_XP_PER_MB, "low priority pack")
                .declare("a:xp", TWO_MB_PER_XP, "high priority pack")
                .build();

        assertEquals(TWO_MB_PER_XP, table.rateFor("a:xp").orElseThrow());
        assertEquals("high priority pack", table.resolve("a:xp").orElseThrow().source());
    }

    @Test
    void anUnregisterRemovesAnEarlierDeclaration() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("a:xp", ONE_XP_PER_MB, "builtin")
                .unregister("a:xp")
                .build();

        assertFalse(table.accepts("a:xp"));
        assertEquals(0, table.declaredCount());
    }

    @Test
    void aDeclarationAfterAnUnregisterBringsTheFluidBack() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("a:xp", ONE_XP_PER_MB, "builtin")
                .unregister("a:xp")
                .declare("a:xp", TWO_MB_PER_XP, "addon pack")
                .build();

        assertEquals(TWO_MB_PER_XP, table.rateFor("a:xp").orElseThrow());
    }

    @Test
    void valueOfATankIsFlooredSoTheRemainderStaysInside() {
        FluidRateTable table = FluidRateTable.builder().declare("a:xp", TWO_MB_PER_XP, "test").build();

        assertEquals(0, table.xpValue("a:xp", 1999));
        assertEquals(1, table.xpValue("a:xp", 2000));
        assertEquals(1, table.xpValue("a:xp", 3999));
    }

    @Test
    void declarationsAreSortedByPackThenRecipeId() {
        List<FluidDeclaration> declarations = new ArrayList<>(List.of(
                new FluidDeclaration("a:xp", ONE_XP_PER_MB, "late pack", 3, "b:late"),
                new FluidDeclaration("a:xp", TWO_MB_PER_XP, "early pack", 1, "a:early")));
        declarations.sort(null);

        assertEquals("a:early", declarations.get(0).recipeId());
        assertEquals("b:late", declarations.get(1).recipeId());
    }

    @Test
    void anUnregisterDeclarationCarriesNoRate() {
        FluidDeclaration declaration = new FluidDeclaration("a:xp", null, "pack: a:kill", 0, "a:kill");

        assertTrue(declaration.isUnregister());
    }
}
