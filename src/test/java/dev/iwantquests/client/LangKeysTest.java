package dev.iwantquests.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Catches typos in UI language keys and malformed bundled themes without starting the game. */
class LangKeysTest {
    private static final Path ROOT = Path.of("src", "main");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z_]+(?:\\.[a-z_]+)*)\"");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void everyUiKeyUsedInCodeExistsInTheLanguageFile() throws IOException {
        JsonObject lang = json(ROOT.resolve("resources/assets/iwantquests/lang/en_us.json"));
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int call = line.indexOf("QuestText.t(");
                    while (call >= 0) {
                        int start = call + "QuestText.t(".length(), depth = 1, end = start;
                        while (end < line.length() && depth > 0) {
                            char c = line.charAt(end++);
                            if (c == '(') depth++;
                            else if (c == ')') depth--;
                        }
                        Matcher matcher = LITERAL.matcher(line.substring(start, end));
                        while (matcher.find()) {
                            String key = "iwantquests.ui." + matcher.group(1);
                            if (!lang.has(key)) missing.add(key + "  (" + file.getFileName() + ")");
                        }
                        call = line.indexOf("QuestText.t(", end);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing language keys: " + missing);
    }

    @Test
    void translatableComponentKeysExist() throws IOException {
        JsonObject lang = json(ROOT.resolve("resources/assets/iwantquests/lang/en_us.json"));
        Pattern translatable = Pattern.compile("translatable\\(\"([a-z_.]+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = translatable.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) if (!lang.has(matcher.group(1))) missing.add(matcher.group(1));
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void bundledThemesAreValidJsonObjects() throws IOException {
        Path dir = ROOT.resolve("resources/assets/iwantquests/iwantquests/themes");
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> themes =
                    files.filter(p -> p.toString().endsWith(".json")).toList();
            assertTrue(themes.size() >= 3, "default, midnight and classic should ship");
            for (Path theme : themes) {
                JsonObject object = json(theme);
                for (String section : object.keySet()) {
                    assertTrue(
                            Set.of("parent", "colors", "sprites", "shapes", "layout", "options")
                                    .contains(section),
                            theme.getFileName() + " has unknown section " + section);
                }
            }
        }
    }
}
