package com.olynk.waystonexppayment.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

/** Lookup order and parsing rules of the fluid -> XP rate table. */
class FluidRateTableTest {

    private static final String CEI = "create_enchantment_industry:experience";

    @Test
    void exactFluidIdWinsOverTag() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("#c:experience", 10)
                .declare(CEI, 1)
                .build();

        assertEquals(OptionalInt.of(1), table.rateFor(CEI, List.of("c:experience")));
    }

    @Test
    void declaredTagMatches() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("#c:experience", 1)
                .build();

        assertEquals(OptionalInt.of(1), table.rateFor("some_mod:xp", List.of("c:experience")));
    }

    @Test
    void undeclaredFluidIsRejectedByDefault() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("#c:experience", 1)
                .build();

        assertFalse(table.accepts("some_mod:not_xp", List.of("forge:experience")));
        assertEquals(0, table.xpValue("some_mod:not_xp", List.of(), 1000));
    }

    @Test
    void undeclaredFluidUsesFallbackWhenOptedIn() {
        FluidRateTable table = FluidRateTable.builder()
                .acceptUndeclared(true)
                .fallbackMbPerXp(10)
                .build();

        assertEquals(OptionalInt.of(10), table.rateFor("some_mod:xp", List.of()));
        assertEquals(100, table.xpValue("some_mod:xp", List.of(), 1000));
    }

    @Test
    void xpValueFloorsAndKeepsTheRemainderInTheTank() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("#c:experience", 10)
                .build();

        assertEquals(99, table.xpValue("some_mod:xp", List.of("c:experience"), 999));
    }

    @Test
    void declaredEntriesAreReportedForTagMatching() {
        FluidRateTable table = FluidRateTable.builder()
                .declare("#c:experience", 1)
                .declare(CEI, 1)
                .build();

        assertTrue(table.declaredTags().contains("c:experience"));
        assertTrue(table.declaredFluidIds().contains(CEI));
        assertEquals(2, table.declaredCount());
    }

    @Test
    void parseEntryAcceptsBothForms() {
        Optional<java.util.Map.Entry<String, Integer>> fluid = FluidRateTable.parseEntry(CEI + "=1");
        Optional<java.util.Map.Entry<String, Integer>> tag = FluidRateTable.parseEntry("#c:experience = 10");

        assertEquals(CEI, fluid.orElseThrow().getKey());
        assertEquals(1, fluid.orElseThrow().getValue());
        assertEquals("#c:experience", tag.orElseThrow().getKey());
        assertEquals(10, tag.orElseThrow().getValue());
    }

    @Test
    void parseEntryRejectsMalformedLines() {
        assertTrue(FluidRateTable.parseEntry(null).isEmpty());
        assertTrue(FluidRateTable.parseEntry("").isEmpty());
        assertTrue(FluidRateTable.parseEntry("no_separator").isEmpty());
        assertTrue(FluidRateTable.parseEntry("=1").isEmpty());
        assertTrue(FluidRateTable.parseEntry("a=abc").isEmpty());
        assertTrue(FluidRateTable.parseEntry("a=0").isEmpty(), "a rate below 1 mB per XP is meaningless");
    }

    @Test
    void declareAllSkipsMalformedEntries() {
        FluidRateTable table = FluidRateTable.builder()
                .declareAll(List.of(CEI + "=1", "garbage", "#c:experience=2"))
                .build();

        assertEquals(2, table.declaredCount());
        assertEquals(OptionalInt.of(1), table.rateFor(CEI, List.of()));
    }

    @Test
    void builderRejectsRatesBelowOne() {
        FluidRateTable table = FluidRateTable.builder().declare(CEI, 0).build();

        assertEquals(OptionalInt.of(1), table.rateFor(CEI, List.of()));
    }

    @Test
    void defaultRateIsOneMilliBucketPerXp() {
        // 1 mB = 1 XP mirrors Create: Enchantment Industry's own experience fluid.
        assertEquals(1, FluidRateTable.DEFAULT_MB_PER_XP);

        FluidRateTable table = FluidRateTable.builder().build();
        assertEquals(1, table.fallbackMbPerXp());
        assertEquals(0, table.declaredCount());
    }
}
