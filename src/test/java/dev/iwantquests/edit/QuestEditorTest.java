package dev.iwantquests.edit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.iwantquests.edit.QuestEditor.*;
import dev.iwantquests.quest.QuestCatalog;
import dev.iwantquests.quest.QuestFiles;
import dev.iwantquests.quest.QuestPack;
import dev.iwantquests.quest.QuestPack.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestEditorTest {
    private static final Objective OBJECTIVE = Objective.simple("main", "obtain", "minecraft:stone", 1, "");

    @TempDir
    Path temp;

    private Path quests, backups;
    private QuestEditor editor;
    private QuestPack live;

    private static Quest quest(String id, String chapter, double x, double y, String... prerequisites) {
        return new Quest(
                id,
                chapter,
                "Quest " + id,
                "",
                "minecraft:stone",
                x,
                y,
                List.of(prerequisites),
                List.of(OBJECTIVE),
                List.of(),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
    }

    private static Chapter chapter(String id) {
        return new Chapter(id, "Chapter " + id, "minecraft:book", 0, null, null, null, null);
    }

    private static QuestCatalog validate(QuestPack pack) {
        return new QuestCatalog(pack, (kind, target) -> {
            if (target == null || !target.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid target " + target);
        });
    }

    private void write(String name, QuestPack pack) throws IOException {
        Files.createDirectories(quests);
        Files.writeString(quests.resolve(name), QuestFiles.toJson(pack), StandardCharsets.UTF_8);
    }

    /** Loads what the loader would: the (read-only) bundled pack first, then every config file. */
    private QuestPack merged(QuestPack bundled) throws IOException {
        List<Chapter> chapters = new java.util.ArrayList<>(bundled.chapters());
        List<Quest> all = new java.util.ArrayList<>(bundled.quests());
        for (QuestPack pack : QuestFiles.loadDirectory(quests)) {
            chapters.addAll(pack.chapters());
            all.addAll(pack.quests());
        }
        return new QuestPack(chapters, all);
    }

    private static JsonObject set(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static PatchRequest patch(String questId, String json) {
        return new PatchRequest(List.of(new Patch(questId, set(json))), null);
    }

    @BeforeEach
    void setUp() throws IOException {
        quests = temp.resolve("quests");
        backups = temp.resolve("backups");
        editor = new QuestEditor(quests, backups, QuestEditorTest::validate, () -> "custom");
        QuestPack bundled =
                new QuestPack(List.of(chapter("base:intro")), List.of(quest("base:start", "base:intro", 0, 0)));
        write(
                "mine.json",
                new QuestPack(
                        List.of(chapter("mine:ch")),
                        List.of(
                                quest("mine:a", "mine:ch", 0, 0),
                                quest("mine:b", "mine:ch", 100, 0, "mine:a"),
                                quest("mine:c", "mine:ch", 200, 0, "mine:b"))));
        live = merged(bundled);
    }

    private Quest find(QuestPack pack, String id) {
        return pack.quests().stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void movingAQuestRewritesItsFileAndTheLivePack() throws IOException {
        Outcome outcome = editor.patch(live, patch("mine:a", "{\"x\":48,\"y\":-16}"));
        assertEquals(48, find(outcome.pack(), "mine:a").x());
        assertEquals(-16, find(outcome.pack(), "mine:a").y());
        assertEquals("Moved 1 quest", outcome.message());
        assertEquals(
                48, find(QuestFiles.loadDirectory(quests).getFirst(), "mine:a").x());
    }

    @Test
    void bundledQuestsAreReadOnly() {
        var error = assertThrows(
                IllegalArgumentException.class, () -> editor.patch(live, patch("base:start", "{\"x\":5}")));
        assertTrue(error.getMessage().contains("read-only"), error.getMessage());
    }

    @Test
    void unknownQuestIsNotReportedAsReadOnly() {
        var error = assertThrows(
                IllegalArgumentException.class, () -> editor.patch(live, patch("mine:missing", "{\"x\":5}")));
        assertTrue(error.getMessage().contains("Unknown quest"), error.getMessage());
    }

    @Test
    void editableListsOnlyConfigFileContent() {
        Editable editable = editor.editable();
        assertTrue(editable.quests().containsAll(List.of("mine:a", "mine:b", "mine:c")));
        assertFalse(editable.quests().contains("base:start"));
        assertEquals(java.util.Set.of("mine:ch"), editable.chapters());
    }

    @Test
    void invalidChangeLeavesFilesUntouched() throws IOException {
        String before = Files.readString(quests.resolve("mine.json"));
        assertThrows(IllegalArgumentException.class, () -> editor.patch(live, patch("mine:a", "{\"title\":\"  \"}")));
        assertEquals(before, Files.readString(quests.resolve("mine.json")));
        assertFalse(editor.canUndo());
    }

    @Test
    void prerequisiteCyclesAreRejected() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> editor.patch(live, patch("mine:a", "{\"prerequisites\":[\"mine:c\"]}")));
        assertTrue(error.getMessage().contains("Circular"), error.getMessage());
    }

    @Test
    void idsCannotBeChangedAndUnknownFieldsAreRejected() {
        assertTrue(assertThrows(
                        IllegalArgumentException.class,
                        () -> editor.patch(live, patch("mine:a", "{\"id\":\"mine:z\"}")))
                .getMessage()
                .contains("cannot be changed"));
        assertTrue(assertThrows(
                        IllegalArgumentException.class,
                        () -> editor.patch(live, patch("mine:a", "{\"colour\":\"red\"}")))
                .getMessage()
                .contains("Unknown field"));
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> editor.patch(live, patch("mine:a", "{\"x\":\"far\"}")))
                .getMessage()
                .contains("Invalid value"));
    }

    @Test
    void objectivesAndRewardsCanBeReplaced() {
        Outcome outcome = editor.patch(
                live,
                patch(
                        "mine:a",
                        "{\"objectives\":[{\"id\":\"wood\",\"type\":\"obtain\",\"target\":\"#minecraft:logs\",\"count\":8}],"
                                + "\"rewards\":[{\"type\":\"xp\",\"count\":50}]}"));
        Quest a = find(outcome.pack(), "mine:a");
        assertEquals("#minecraft:logs", a.objectives().getFirst().target());
        assertEquals(50, a.rewards().getFirst().count());
    }

    @Test
    void newQuestsJoinTheFileOfTheirChapterWithAUniqueId() throws IOException {
        Outcome first = editor.createQuest(live, new NewQuest("mine:ch", 16, 32, "Dig Deep", null));
        assertEquals("mine:dig_deep", first.select());
        Outcome second = editor.createQuest(first.pack(), new NewQuest("mine:ch", 0, 0, "Dig Deep", List.of("mine:a")));
        assertEquals("mine:dig_deep_2", second.select());
        assertEquals(List.of("mine:a"), find(second.pack(), "mine:dig_deep_2").prerequisites());
        assertFalse(Files.exists(quests.resolve("editor.json")));
        assertEquals(5, QuestFiles.loadDirectory(quests).getFirst().quests().size());
    }

    @Test
    void newQuestsInABundledChapterGoToTheEditorFile() throws IOException {
        Outcome outcome = editor.createQuest(live, new NewQuest("base:intro", 0, 0, null, null));
        assertEquals("base:new_quest", outcome.select());
        assertTrue(Files.exists(quests.resolve("editor.json")));
        assertEquals("base:intro", find(outcome.pack(), "base:new_quest").chapter());
    }

    @Test
    void newChaptersGetTheConfiguredNamespaceAndTheNextOrder() {
        Outcome outcome = editor.createChapter(live, new NewChapter("Nether Trip", null));
        assertEquals("custom:nether_trip", outcome.select());
        Chapter created = outcome.pack().chapters().stream()
                .filter(c -> c.id().equals("custom:nether_trip"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, created.order());
        assertEquals("minecraft:book", created.icon());
    }

    @Test
    void deletingAQuestRemovesItFromPrerequisites() {
        Outcome outcome = editor.delete(live, new DeleteRequest(List.of("mine:b"), null));
        assertNull(outcome.catalog().quest("mine:b"));
        assertEquals(List.of(), find(outcome.pack(), "mine:c").prerequisites());
        assertEquals("Deleted 1 quest", outcome.message());
    }

    @Test
    void deletingAChapterDeletesItsQuests() {
        Outcome outcome = editor.delete(live, new DeleteRequest(null, List.of("mine:ch")));
        assertEquals(
                List.of("base:start"),
                outcome.pack().quests().stream().map(Quest::id).toList());
    }

    @Test
    void deletingAQuestThatBundledContentNeedsIsRejected() throws IOException {
        QuestPack bundled = new QuestPack(
                List.of(chapter("base:intro")),
                List.of(quest("base:start", "base:intro", 0, 0), quest("base:next", "base:intro", 0, 0, "mine:a")));
        QuestPack withDependent = merged(bundled);
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> editor.delete(withDependent, new DeleteRequest(List.of("mine:a"), null)));
        assertTrue(error.getMessage().contains("missing prerequisite"), error.getMessage());
    }

    @Test
    void undoAndRedoRestoreFilesAndLivePack() throws IOException {
        String original = Files.readString(quests.resolve("mine.json"));
        Outcome moved = editor.patch(live, patch("mine:a", "{\"x\":64}"));
        Outcome undone = editor.undo(moved.pack());
        assertEquals(0, find(undone.pack(), "mine:a").x());
        assertEquals(original, Files.readString(quests.resolve("mine.json")));
        assertTrue(undone.message().startsWith("Undid:"));
        assertFalse(editor.canUndo());
        Outcome redone = editor.redo(undone.pack());
        assertEquals(64, find(redone.pack(), "mine:a").x());
        assertTrue(editor.canUndo());
        assertFalse(editor.canRedo());
    }

    @Test
    void undoingTheFirstEditorFileCreationRemovesTheFile() {
        Outcome created = editor.createChapter(live, new NewChapter("Fresh", null));
        assertTrue(Files.exists(quests.resolve("editor.json")));
        Outcome undone = editor.undo(created.pack());
        assertFalse(Files.exists(quests.resolve("editor.json")));
        assertNull(undone.catalog().quest("custom:fresh"));
    }

    @Test
    void aNewEditClearsTheRedoHistory() {
        Outcome moved = editor.patch(live, patch("mine:a", "{\"x\":64}"));
        Outcome undone = editor.undo(moved.pack());
        assertTrue(editor.canRedo());
        editor.patch(undone.pack(), patch("mine:a", "{\"x\":80}"));
        assertFalse(editor.canRedo());
        assertThrows(IllegalArgumentException.class, () -> editor.redo(live));
    }

    @Test
    void undoDoesNotOverwriteExternalEdits() throws IOException {
        Outcome moved = editor.patch(live, patch("mine:a", "{\"x\":64}"));
        String external = Files.readString(quests.resolve("mine.json")).replace("Quest mine:a", "External title");
        Files.writeString(quests.resolve("mine.json"), external);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> editor.undo(moved.pack()))
                .getMessage()
                .contains("changed outside the editor"));
        assertEquals(external, Files.readString(quests.resolve("mine.json")));
        assertTrue(editor.canUndo());
        assertFalse(editor.canRedo());
    }

    @Test
    void redoDoesNotOverwriteExternallyCreatedFiles() throws IOException {
        Outcome created = editor.createChapter(live, new NewChapter("Fresh", null));
        Outcome undone = editor.undo(created.pack());
        String external = "{\"chapters\":[],\"quests\":[]}\n";
        Files.writeString(editor.editorFile(), external);
        assertThrows(IllegalArgumentException.class, () -> editor.redo(undone.pack()));
        assertEquals(external, Files.readString(editor.editorFile()));
        assertTrue(editor.canRedo());
    }

    @Test
    void externalReloadCanClearBothHistoryStacks() {
        Outcome moved = editor.patch(live, patch("mine:a", "{\"x\":64}"));
        editor.undo(moved.pack());
        assertTrue(editor.canRedo());
        editor.clearHistory();
        assertFalse(editor.canUndo());
        assertFalse(editor.canRedo());
    }

    @Test
    void brokenFileAfterAValidFileMakesAllContentReadOnly() throws IOException {
        Files.writeString(quests.resolve("z-broken.json"), "{broken");
        assertTrue(editor.editable().quests().isEmpty());
        assertTrue(editor.editable().chapters().isEmpty());
    }

    @Test
    void replacedFilesAreBackedUpOutsideTheQuestFolder() throws IOException {
        editor.patch(live, patch("mine:a", "{\"x\":64}"));
        try (Stream<Path> files = Files.list(backups)) {
            assertEquals(
                    1,
                    files.filter(p -> p.getFileName().toString().startsWith("mine.json."))
                            .count());
        }
        try (Stream<Path> files = Files.walk(quests)) {
            assertEquals(
                    List.of("mine.json"),
                    files.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .toList());
        }
    }

    @Test
    void aBrokenConfigFileBlocksEditsAndNamesTheFile() throws IOException {
        Files.writeString(quests.resolve("broken.json"), "{ not json");
        var error =
                assertThrows(IllegalArgumentException.class, () -> editor.patch(live, patch("mine:a", "{\"x\":1}")));
        assertTrue(error.getMessage().contains("broken.json"), error.getMessage());
        assertTrue(editor.editable().quests().isEmpty());
    }

    @Test
    void slugsAreSafeIdPaths() {
        assertEquals("dig_deep", QuestEditor.slug("  Dig Deep!! ", "quest"));
        assertEquals("quest", QuestEditor.slug("§§§", "quest"));
        assertTrue(QuestEditor.slug("x".repeat(200), "quest").length() <= 40);
    }
}
