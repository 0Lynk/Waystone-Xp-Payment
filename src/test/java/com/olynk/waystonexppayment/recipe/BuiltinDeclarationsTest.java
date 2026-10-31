package com.olynk.waystonexppayment.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Guards the declarations this mod ships for the fluids it has been tested against.
 *
 * <p>They are ordinary datapack files, so nothing compiles them: a typo in an id or a rate written in the old
 * unit would only surface as "the tank cannot pay" in someone's world. Reading them back as a pack would does
 * the same job without a running server.
 */
class BuiltinDeclarationsTest {

    private static final Path BUILTIN_DIR =
            Path.of("src/main/resources/data/waystone_xp_payment/recipe/builtin");

    private static final String RECIPE_TYPE = "waystone_xp_payment:xp_fluid";

    @Test
    void everyShippedDeclarationIsAWellFormedRecipe() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Path file : files()) {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = file.getFileName().toString();
            if (!RECIPE_TYPE.equals(json.get("type").getAsString())) {
                problems.add(name + ": type is " + json.get("type").getAsString());
            }
            String fluid = json.get("fluid").getAsString();
            if (!fluid.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                problems.add(name + ": fluid id '" + fluid + "' is not a resource location");
            }
            double perBucket = json.get("xp_per_bucket").getAsDouble();
            if (perBucket <= 0 || perBucket > 1_000_000) {
                problems.add(name + ": xp_per_bucket " + perBucket + " is outside 1..1,000,000");
            }
        }

        assertTrue(problems.isEmpty(), "shipped declarations with problems: " + problems);
    }

    @Test
    void theShippedSetCoversTheFluidsTheModDocuments() throws IOException {
        List<String> fluids = new ArrayList<>();
        for (Path file : files()) {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            fluids.add(json.get("fluid").getAsString());
        }

        assertEquals(fluids.size(), fluids.stream().distinct().count(), "a fluid must not be declared twice");
        assertTrue(fluids.contains("create_enchantment_industry:experience"),
                "the fluid the mod is built around must ship with a rate");
    }

    @Test
    void theBuiltinFolderIsNotAccidentallyEmpty() throws IOException {
        assertFalse(files().isEmpty(), "a build without builtin declarations makes every tank worthless");
    }

    @Test
    void bothFluidsThatShippedAsPlaceholdersNowCarryTheMeasuredRate() throws IOException {
        assertEquals(10.0, rateOf("beyonddimensions:xp_fluid"));
        assertEquals(10.0, rateOf("sophisticatedcore:xp_still"));
    }

    private static double rateOf(String fluid) throws IOException {
        for (Path file : files()) {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (fluid.equals(json.get("fluid").getAsString())) {
                return json.get("xp_per_bucket").getAsDouble();
            }
        }
        throw new AssertionError("no builtin declaration for " + fluid);
    }

    private static List<Path> files() throws IOException {
        try (var stream = Files.list(BUILTIN_DIR)) {
            return stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }
}
