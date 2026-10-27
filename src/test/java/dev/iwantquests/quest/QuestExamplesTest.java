package dev.iwantquests.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The example files are copied by modpack authors, so each one must load and validate on its own. */
class QuestExamplesTest {
    @Test
    void everyExampleQuestFileLoadsAndValidates() throws Exception {
        List<Path> files;
        try (var paths = Files.walk(Path.of("examples"))) {
            files = paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertFalse(files.isEmpty(), "no example quest files found");
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                QuestPack pack = QuestFiles.read(reader);
                assertDoesNotThrow(
                        () -> new QuestCatalog(pack, (kind, target) -> {
                            if (target == null || !target.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                                throw new IllegalArgumentException("Invalid target " + target);
                        }),
                        file.toString());
            }
        }
    }
}
