package com.olynk.waystonexppayment.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * Guards the one mixin mistake that compiles cleanly and then kills the client.
 *
 * <p>{@code @Shadow} resolves against the members the <em>target class itself</em> declares. Shadowing an
 * inherited member is accepted by the compiler and then fails Mixin's APPLY phase the first time the target class
 * is loaded, which for a screen widget means the screen never opens and the client disconnects. That is exactly
 * what {@code WaystoneButtonClientMixin} did with {@code active} (declared in {@code AbstractWidget}, not in
 * {@code WaystoneButton}) - a crash that only showed up months after the code was written, because it needs
 * someone to open that screen.
 *
 * <p>The check runs the sources against the real classes: Waystones is an {@code implementation} dependency and
 * the NeoForge artifact carries the vanilla client classes, so both are on the test runtime classpath. When a
 * target class cannot be loaded here the test aborts rather than failing, so a classpath change shows up as a
 * skipped guard instead of a red build.
 */
class MixinShadowPolicyTest {

    private static final Path MIXIN_DIR =
            Path.of("src", "main", "java", "com", "olynk", "waystonexppayment", "mixin");
    private static final Pattern MIXIN_TARGET = Pattern.compile("@Mixin\\s*\\(\\s*([\\w.]+)\\.class\\s*\\)");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    /** Anchored: the annotation is only real code when a line starts with it (comments mention it in prose). */
    private static final Pattern SHADOW_LINE = Pattern.compile("^\\s*@Shadow\\b", Pattern.MULTILINE);
    private static final Pattern TRAILING_NAME = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$");

    @Test
    void everyShadowedMemberIsDeclaredByItsTargetClass() throws IOException {
        assertTrue(Files.isDirectory(MIXIN_DIR), "mixin sources not found at " + MIXIN_DIR.toAbsolutePath());

        List<Path> sources;
        try (Stream<Path> files = Files.list(MIXIN_DIR)) {
            sources = files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }

        int targets = 0;
        int shadows = 0;
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            Matcher mixin = MIXIN_TARGET.matcher(text);
            if (!mixin.find()) {
                continue;
            }
            targets++;
            String targetName = resolveClassName(mixin.group(1), text, source);
            Class<?> targetClass = load(targetName);
            Set<String> declared = declaredMemberNames(targetClass);
            for (String shadow : shadowedNames(text)) {
                shadows++;
                assertTrue(declared.contains(shadow), () -> source.getFileName() + " shadows '" + shadow + "', but "
                        + targetName + " does not declare it. @Shadow only resolves the members the target class"
                        + " declares itself; an inherited one fails Mixin APPLY when the class is first loaded."
                        + " Reach inherited members through a cast instead.");
            }
        }

        assertTrue(targets > 0, "no @Mixin target was found under " + MIXIN_DIR + " - did the mixins move?");
        Assumptions.assumeTrue(shadows > 0, "no @Shadow member is declared by any mixin right now");
    }

    /** Simple names come from the mixin's own imports, which is how the compiler resolved them too. */
    private static String resolveClassName(String name, String source, Path file) {
        if (name.contains(".")) {
            return name;
        }
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            if (imports.group(1).endsWith("." + name)) {
                return imports.group(1);
            }
        }
        throw new TestAbortedException("cannot resolve " + name + " from the imports of " + file.getFileName());
    }

    private static Class<?> load(String name) {
        try {
            // initialize = false: these are client classes and running their static initialisers needs a game.
            return Class.forName(name, false, MixinShadowPolicyTest.class.getClassLoader());
        } catch (Throwable failure) {
            throw new TestAbortedException("cannot load " + name + " in the test runtime (" + failure
                    + "); the mixin shadow check cannot run");
        }
    }

    private static Set<String> declaredMemberNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        Arrays.stream(type.getDeclaredFields()).forEach(field -> names.add(field.getName()));
        try {
            Arrays.stream(type.getDeclaredMethods()).forEach(method -> names.add(method.getName()));
        } catch (Throwable ignored) {
            // A client class can have a signature this JVM cannot resolve; fields still carry the check.
        }
        return names;
    }

    /** Names of everything declared behind an {@code @Shadow}, fields and methods alike. */
    private static List<String> shadowedNames(String source) {
        List<String> names = new ArrayList<>();
        Matcher shadow = SHADOW_LINE.matcher(source);
        while (shadow.find()) {
            int end = shadow.end();
            while (end < source.length() && ";{(".indexOf(source.charAt(end)) < 0) {
                end++;
            }
            if (end == source.length()) {
                continue;
            }
            Matcher name = TRAILING_NAME.matcher(source.substring(shadow.end(), end).trim());
            if (name.find()) {
                names.add(name.group(1));
            }
        }
        return names;
    }
}
