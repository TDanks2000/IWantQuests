package dev.iwantquests.quest;

import static org.junit.jupiter.api.Assertions.*;

import dev.iwantquests.quest.QuestPack.*;
import java.util.*;
import org.junit.jupiter.api.*;

class QuestCatalogCustomizationTest {
    private static final String CHAPTER = "iwantquests:chapter";
    private static final Objective OBJECTIVE =
            new Objective("main", "obtain", "minecraft:stone", 1, "", null, 0, 0, 0, 0, 0, 0);

    private QuestCatalog catalog(QuestPack pack) {
        return new QuestCatalog(pack, (kind, target) -> {
            if (target == null || !target.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid target " + target);
        });
    }

    private Chapter chapter(List<String> prerequisites) {
        return new Chapter(CHAPTER, "Chapter", "minecraft:oak_sapling", 0, null, null, null, prerequisites);
    }

    private Quest quest(String id, String icon, String iconTexture, String shape, double size) {
        return new Quest(
                id,
                CHAPTER,
                "Title",
                "Description",
                icon,
                0,
                0,
                List.of(),
                List.of(OBJECTIVE),
                List.of(),
                false,
                false,
                false,
                false,
                null,
                shape,
                size,
                iconTexture);
    }

    private QuestPack pack(Quest quest) {
        return new QuestPack(List.of(chapter(List.of())), List.of(quest));
    }

    private String rejection(QuestPack pack) {
        return assertThrows(IllegalArgumentException.class, () -> catalog(pack)).getMessage();
    }

    @Test
    void customNamespacedShapeIsAccepted() {
        assertDoesNotThrow(() -> catalog(pack(quest("iwantquests:star", "minecraft:stone", null, "mypack:star", 1))));
    }

    @Test
    void unknownPlainShapeIsRejected() {
        assertTrue(rejection(pack(quest("iwantquests:star", "minecraft:stone", null, "star", 1)))
                .contains("unknown shape star"));
    }

    @Test
    void oversizedNodeIsRejected() {
        assertTrue(rejection(pack(quest("iwantquests:big", "minecraft:stone", null, null, 5)))
                .contains("size must be between 0.5 and 3.0"));
    }

    @Test
    void zeroSizeMeansDefaultAndIsAccepted() {
        assertDoesNotThrow(() -> catalog(pack(quest("iwantquests:default_size", "minecraft:stone", null, null, 0))));
    }

    @Test
    void questWithoutIconOrIconTextureIsRejected() {
        assertTrue(rejection(pack(quest("iwantquests:blank", null, null, null, 1)))
                .contains("icon or iconTexture is required"));
    }

    @Test
    void iconTextureOnlyQuestIsAccepted() {
        assertDoesNotThrow(() -> catalog(pack(quest("iwantquests:sprite", null, "mypack:star_icon", null, 1))));
    }

    @Test
    void chapterPrerequisiteOnMissingQuestIsRejected() {
        QuestPack pack = new QuestPack(
                List.of(chapter(List.of("iwantquests:missing"))),
                List.of(quest("iwantquests:star", "minecraft:stone", null, null, 1)));
        assertTrue(rejection(pack).contains("missing chapter prerequisite iwantquests:missing"));
    }

    @Test
    void chapterPrerequisiteOnExistingQuestIsAccepted() {
        QuestPack pack = new QuestPack(
                List.of(chapter(List.of("iwantquests:star"))),
                List.of(quest("iwantquests:star", "minecraft:stone", null, null, 1)));
        assertDoesNotThrow(() -> catalog(pack));
    }
}
