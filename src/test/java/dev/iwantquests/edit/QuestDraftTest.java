package dev.iwantquests.edit;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class QuestDraftTest {
    @TempDir
    Path temp;

    private Path live;
    private QuestPack pack;

    private static QuestCatalog validate(QuestPack p) {
        return new QuestCatalog(p, (kind, target) -> {});
    }

    private QuestDraft draft(String author) {
        return new QuestDraft(
                live,
                temp.resolve("drafts").resolve(author),
                temp.resolve("backups"),
                QuestDraftTest::validate,
                () -> "iwantquests");
    }

    @BeforeEach
    void init() throws Exception {
        live = temp.resolve("quests");
        Files.createDirectories(live);
        var c = new Chapter("iwantquests:test", "Test", "minecraft:book", 0, null, null, null, null);
        var q = new Quest(
                "iwantquests:a",
                c.id(),
                "A",
                "",
                "minecraft:book",
                0,
                0,
                List.of(),
                List.of(Objective.simple("main", "manual", null, 1, "")),
                List.of(),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
        pack = new QuestPack(List.of(c), List.of(q));
        QuestFiles.write(live.resolve("test.json"), QuestFiles.toJson(pack));
    }

    private void title(QuestDraft d, String title) {
        d.editor()
                .patch(
                        d.pack(pack),
                        new QuestEditor.PatchRequest(
                                List.of(new QuestEditor.Patch(
                                        "iwantquests:a",
                                        JsonParser.parseString("{\"title\":\"" + title + "\"}")
                                                .getAsJsonObject())),
                                null));
    }

    @Test
    void authoringIsPrivateAndSurvivesRestart() throws Exception {
        String before = Files.readString(live.resolve("test.json"));
        QuestDraft d = draft("one");
        title(d, "Draft title");
        assertEquals(before, Files.readString(live.resolve("test.json")));
        assertEquals("Draft title", draft("one").pack(pack).quests().getFirst().title());
        assertEquals("A", draft("two").pack(pack).quests().getFirst().title());
    }

    @Test
    void publishAppliesTheReviewedBatchAndStartsANewBaseline() throws Exception {
        QuestDraft d = draft("one");
        title(d, "Published title");
        assertEquals(1, d.review(pack).changed().size());
        assertFalse(d.review(pack).conflict());
        var result = d.publish(pack);
        assertEquals(
                "Published title",
                QuestFiles.loadDirectory(live).getFirst().quests().getFirst().title());
        assertFalse(d.review(result.pack()).changedAnything());
        try (var backups = Files.list(temp.resolve("backups"))) {
            assertTrue(backups.findAny().isPresent());
        }
    }

    @Test
    void anotherAuthorsPublicationCannotBeOverwritten() {
        QuestDraft a = draft("one"), b = draft("two");
        title(a, "Alice");
        title(b, "Bob");
        var published = a.publish(pack);
        assertTrue(b.review(published.pack()).conflict());
        assertThrows(IllegalArgumentException.class, () -> b.publish(published.pack()));
    }

    @Test
    void externalFileChangesBlockPublicationEvenBeforeReload() throws Exception {
        QuestDraft d = draft("one");
        title(d, "Draft");
        Files.writeString(live.resolve("test.json"), Files.readString(live.resolve("test.json")) + "\n");
        assertTrue(d.review(pack).conflict());
        assertThrows(IllegalArgumentException.class, () -> d.publish(pack));
    }

    @Test
    void discardRemovesOnlyThePrivateWork() throws Exception {
        QuestDraft d = draft("one");
        title(d, "Draft");
        d.discard();
        assertEquals("A", d.pack(pack).quests().getFirst().title());
        assertEquals(
                "A",
                QuestFiles.loadDirectory(live).getFirst().quests().getFirst().title());
    }

    @Test
    void publishingAnUnchangedDraftIsRejected() {
        QuestDraft d = draft("one");
        assertFalse(d.review(pack).changedAnything());
        assertThrows(IllegalArgumentException.class, () -> d.publish(pack));
    }

    @Test
    void draftStorageCannotOverlapPublishedFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestDraft(
                        live, live, temp.resolve("backups"), QuestDraftTest::validate, () -> "iwantquests"));
    }

    @Test
    void malformedLiveFileBlocksPublishWithoutPreventingDraftRecovery() throws Exception {
        QuestDraft d = draft("one");
        title(d, "Draft");
        Files.writeString(live.resolve("test.json"), "broken");
        assertTrue(d.review(pack).conflict());
        assertEquals("Draft", d.pack(pack).quests().getFirst().title());
        assertThrows(IllegalArgumentException.class, () -> d.publish(pack));
    }

    @Test
    void copyingAGroupAssignsUniqueIdsAndRemapsItsInternalLinks() {
        QuestDraft d = draft("one");
        var initial = d.pack(pack);
        var first = d.editor()
                .createQuest(
                        initial, new QuestEditor.NewQuest("iwantquests:test", 40, 20, "B", List.of("iwantquests:a")));
        var copied = d.editor()
                .paste(first.pack(), new QuestEditor.PasteRequest(first.pack().quests(), "iwantquests:test", 100, 100));
        assertEquals(4, copied.pack().quests().size());
        var copies = copied.pack().quests().subList(2, 4);
        assertEquals(List.of(copies.getFirst().id()), copies.getLast().prerequisites());
        assertEquals(40, copies.getLast().x() - copies.getFirst().x());
        assertEquals(2, d.editor().undo(copied.pack()).pack().quests().size());
    }

    @Test
    void templatesSurviveRestartAndRetainACompleteSelection() {
        var templates = new QuestTemplates(temp.resolve("templates"));
        templates.save(new QuestTemplates.Request("Starter", List.of("iwantquests:a")), pack.quests());
        var restored = new QuestTemplates(temp.resolve("templates")).list();
        assertEquals("Starter", restored.getFirst().name());
        assertEquals(pack.quests(), restored.getFirst().quests());
        assertThrows(
                IllegalArgumentException.class,
                () -> templates.save(new QuestTemplates.Request("Bad", List.of("iwantquests:missing")), pack.quests()));
    }

    @Test
    void templateDeletionIsRestrictedToServerGeneratedIds() {
        var templates = new QuestTemplates(temp.resolve("templates"));
        templates.save(new QuestTemplates.Request("Starter", List.of("iwantquests:a")), pack.quests());
        assertThrows(IllegalArgumentException.class, () -> templates.delete("../quests/test"));
        templates.delete(templates.list().getFirst().id());
        assertTrue(templates.list().isEmpty());
    }

    @Test
    void rewardChangesAreFlaggedBeforePublication() {
        QuestDraft d = draft("one");
        d.editor()
                .patch(
                        d.pack(pack),
                        new QuestEditor.PatchRequest(
                                List.of(new QuestEditor.Patch(
                                        "iwantquests:a",
                                        JsonParser.parseString("{\"rewards\":[{\"type\":\"xp\",\"count\":5}]}")
                                                .getAsJsonObject())),
                                null));
        assertTrue(d.review(pack).progressWarnings().stream().anyMatch(w -> w.contains("claims remain claimed")));
    }
}
