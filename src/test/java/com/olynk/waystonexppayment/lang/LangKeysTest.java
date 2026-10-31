package com.olynk.waystonexppayment.lang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Guards the two translation files against drifting apart.
 *
 * <p>Nothing compiles a lang file: a key renamed on one side, a sentence translated with one placeholder fewer, or
 * an English line that simply never got translated all ship silently, and only show up as a raw key or a broken
 * sentence in someone's client. Reading the files back has the same effect without launching the game.
 */
class LangKeysTest {

    private static final Path LANG_DIR = Path.of("src/main/resources/assets/waystone_xp_payment/lang");
    private static final Path EN = LANG_DIR.resolve("en_us.json");
    private static final Path ZH = LANG_DIR.resolve("zh_cn.json");

    private static final Pattern PLACEHOLDER = Pattern.compile("%\\d*\\$?[sd]");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    @Test
    void bothFilesCarryTheSameKeys() throws IOException {
        JsonObject en = read(EN);
        JsonObject zh = read(ZH);

        List<String> problems = new ArrayList<>();
        for (String key : en.keySet()) {
            if (!zh.has(key)) {
                problems.add("zh_cn is missing " + key);
            }
        }
        for (String key : zh.keySet()) {
            if (!en.has(key)) {
                problems.add("en_us is missing " + key);
            }
        }

        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }

    @Test
    void everyLineAsksForTheSameArguments() throws IOException {
        JsonObject en = read(EN);
        JsonObject zh = read(ZH);

        List<String> problems = new ArrayList<>();
        for (String key : en.keySet()) {
            if (!zh.has(key)) {
                continue;
            }
            int expected = placeholders(en.get(key).getAsString());
            int actual = placeholders(zh.get(key).getAsString());
            if (expected != actual) {
                problems.add(key + ": en_us has " + expected + " placeholders, zh_cn has " + actual);
            }
        }

        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }

    @Test
    void theEnglishFileHasNoChineseInIt() throws IOException {
        JsonObject en = read(EN);

        List<String> problems = new ArrayList<>();
        for (String key : en.keySet()) {
            String value = en.get(key).getAsString();
            if (CJK.matcher(value).find()) {
                problems.add(key + " = " + value);
            }
        }

        assertTrue(problems.isEmpty(), "en_us contains Chinese: " + String.join("; ", problems));
    }

    @Test
    void theChineseFileTranslatesEveryWordedLine() throws IOException {
        JsonObject en = read(EN);
        JsonObject zh = read(ZH);

        List<String> problems = new ArrayList<>();
        for (String key : en.keySet()) {
            String english = en.get(key).getAsString();
            // A line without words carries nothing to translate: "..." reads the same in every language.
            if (!LATIN.matcher(english).find()) {
                continue;
            }
            if (!zh.has(key) || !CJK.matcher(zh.get(key).getAsString()).find()) {
                problems.add(key + " = " + english);
            }
        }

        assertTrue(problems.isEmpty(), "zh_cn left these untranslated: " + String.join("; ", problems));
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
