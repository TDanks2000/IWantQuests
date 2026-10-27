package dev.iwantquests.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes editable quest files without depending on a Minecraft client or resource pack. */
public final class QuestFiles {
    private static final Gson JSON = new Gson();
    private static final Gson PRETTY =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A quest file on disk together with what it currently contains. */
    public record Source(Path path, QuestPack pack) {}

    public static QuestPack read(Reader reader) {
        QuestPack pack = JSON.fromJson(reader, QuestPack.class);
        if (pack == null || pack.chapters() == null || pack.quests() == null)
            throw new IllegalArgumentException("Expected chapters and quests arrays");
        return pack;
    }

    public static List<QuestPack> loadDirectory(Path directory) throws IOException {
        return loadSources(directory).stream().map(Source::pack).toList();
    }

    /** Every {@code .json} file below {@code directory}, in the order the loader merges them. */
    public static List<Source> loadSources(Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Source> sources = new ArrayList<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    sources.add(new Source(path, read(reader)));
                } catch (Exception error) {
                    throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
                }
            }
        }
        return List.copyOf(sources);
    }

    public static String toJson(QuestPack pack) {
        return PRETTY.toJson(pack) + "\n";
    }

    /** Replaces the file in one step, so a crash never leaves a half-written quest file behind. */
    public static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private QuestFiles() {}
}
