package dev.iwantquests.edit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.iwantquests.quest.QuestCatalog;
import dev.iwantquests.quest.QuestFiles;
import dev.iwantquests.quest.QuestFiles.Source;
import dev.iwantquests.quest.QuestPack;
import dev.iwantquests.quest.QuestPack.Chapter;
import dev.iwantquests.quest.QuestPack.Objective;
import dev.iwantquests.quest.QuestPack.Quest;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Applies validated authoring changes to an owned quest directory (normally a private draft).
 * <p>
 * Every change is validated as a whole before anything is written: the merged quest pack (bundled content,
 * data packs and config files together) must build into a valid catalogue, otherwise nothing changes on disk.
 * Only quests and chapters that live in a staged config file can be edited; content from the mod jar or a data pack is
 * read-only. Each accepted change is one undo step, and the previous text of every rewritten file is backed up.
 * Not thread-safe: call from the server thread.
 */
public final class QuestEditor {
    private static final Gson GSON = new Gson();
    private static final int HISTORY = 50, BACKUPS = 100;
    private static final String NEW_QUEST = "New Quest",
            NEW_CHAPTER = "New Chapter",
            PLACEHOLDER_ICON = "minecraft:book";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /** Quest and chapter IDs the editor may change. */
    public record Editable(Set<String> quests, Set<String> chapters) {}

    /** The accepted change: the merged pack, its validated catalogue, a message for the editor and an ID to select. */
    public record Outcome(QuestPack pack, QuestCatalog catalog, String message, String select) {}

    public record Patch(String id, JsonObject set) {}

    public record PatchRequest(List<Patch> quests, List<Patch> chapters) {}

    public record NewQuest(String chapter, double x, double y, String title, List<String> prerequisites) {}

    public record NewChapter(String title, String icon) {}

    public record DeleteRequest(List<String> quests, List<String> chapters) {}

    public record PasteRequest(List<Quest> quests, String chapter, double x, double y) {}

    /** Copies a group as one undo step, remapping internal links and assigning new stable IDs. */
    public Outcome paste(QuestPack live, PasteRequest request) {
        if (request.quests() == null
                || request.quests().isEmpty()
                || request.quests().size() > 128)
            throw new IllegalArgumentException("Choose between 1 and 128 quests to copy.");
        Chapter chapter = live.chapters().stream()
                .filter(c -> c.id().equals(request.chapter()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose a destination chapter."));
        Work work = work(live);
        Draft target = work.holderOfChapterOrNull(chapter.id());
        if (target == null) target = work.draftFor(editorFile());
        Set<String> taken = live.quests().stream().map(Quest::id).collect(Collectors.toSet());
        Map<String, String> ids = new LinkedHashMap<>();
        for (Quest q : request.quests()) {
            if (q == null || q.id() == null || q.title() == null || q.title().isBlank() || ids.containsKey(q.id()))
                throw new IllegalArgumentException("Invalid copied quests.");
            String id = unique(namespaceOf(chapter.id()), slug(q.title(), "quest"), taken);
            ids.put(q.id(), id);
            taken.add(id);
        }
        double minX = request.quests().stream().mapToDouble(Quest::x).min().orElse(0);
        double minY = request.quests().stream().mapToDouble(Quest::y).min().orElse(0);
        Set<String> existing = live.quests().stream().map(Quest::id).collect(Collectors.toSet());
        for (Quest q : request.quests()) {
            List<String> links = (q.prerequisites() == null ? List.<String>of() : q.prerequisites())
                    .stream()
                            .filter(id -> ids.containsKey(id) || existing.contains(id))
                            .map(id -> ids.getOrDefault(id, id))
                            .toList();
            target.quests.add(new Quest(
                    ids.get(q.id()),
                    chapter.id(),
                    q.title(),
                    q.description(),
                    q.icon(),
                    request.x() + q.x() - minX,
                    request.y() + q.y() - minY,
                    links,
                    q.objectives(),
                    q.rewards(),
                    q.optional(),
                    q.hidden(),
                    q.retroactive(),
                    q.repeatable(),
                    q.subtitle(),
                    q.shape(),
                    q.size(),
                    q.iconTexture()));
        }
        return commit(
                live,
                work,
                "Copied " + count(ids.size(), "quest"),
                ids.values().iterator().next());
    }

    /** Validated batch replacement used only by the server-owned draft publisher. */
    public Outcome replaceFiles(QuestPack live, Map<Path, String> contents) {
        return replay(live, contents, "Published quest draft");
    }

    /** File contents before and after one change; a null text means the file does not exist. */
    private record Step(String label, Map<Path, String> before, Map<Path, String> after) {}

    private static final class Draft {
        final List<Chapter> chapters;
        final List<Quest> quests;

        Draft(QuestPack pack) {
            chapters = new ArrayList<>(pack.chapters());
            quests = new ArrayList<>(pack.quests());
        }

        QuestPack pack() {
            return new QuestPack(List.copyOf(chapters), List.copyOf(quests));
        }
    }

    private final Path directory, backups;
    private final Function<QuestPack, QuestCatalog> validator;
    private final Supplier<String> namespace;
    private final Deque<Step> undoSteps = new ArrayDeque<>(), redoSteps = new ArrayDeque<>();

    /**
     * @param directory the quest folder that is edited
     * @param backups where replaced files are copied; must not be inside {@code directory}, which would load them
     * @param validator builds the catalogue for a merged pack, throwing {@link IllegalArgumentException} if it is invalid
     * @param namespace the namespace for new chapter IDs
     */
    public QuestEditor(
            Path directory, Path backups, Function<QuestPack, QuestCatalog> validator, Supplier<String> namespace) {
        this.directory = directory;
        this.backups = backups;
        this.validator = validator;
        this.namespace = namespace;
    }

    public Path editorFile() {
        return directory.resolve("editor.json");
    }

    /** What the config files define right now. Empty when a file cannot be read; edits then report the broken file. */
    public Editable editable() {
        Set<String> quests = new LinkedHashSet<>(), chapters = new LinkedHashSet<>();
        try {
            for (Source source : QuestFiles.loadSources(directory)) {
                source.pack().quests().forEach(q -> quests.add(q.id()));
                source.pack().chapters().forEach(c -> chapters.add(c.id()));
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // The next edit reports the file; the editor simply shows everything as read-only until it is fixed.
            quests.clear();
            chapters.clear();
        }
        return new Editable(quests, chapters);
    }

    public boolean canUndo() {
        return !undoSteps.isEmpty();
    }

    public boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    public void clearHistory() {
        undoSteps.clear();
        redoSteps.clear();
    }

    // ---------------------------------------------------------------- changes

    /** Changes fields of existing quests and chapters, such as positions, text, prerequisites, objectives or rewards. */
    public Outcome patch(QuestPack live, PatchRequest request) {
        List<Patch> quests = request.quests() == null ? List.of() : request.quests();
        List<Patch> chapters = request.chapters() == null ? List.of() : request.chapters();
        if (quests.isEmpty() && chapters.isEmpty()) throw new IllegalArgumentException("Nothing to change.");
        Work work = work(live);
        for (Patch patch : quests) {
            Draft draft = work.holderOfQuest(patch.id());
            int index = indexOf(draft.quests, Quest::id, patch.id());
            draft.quests.set(index, apply(draft.quests.get(index), patch, Quest.class, "quest"));
        }
        for (Patch patch : chapters) {
            Draft draft = work.holderOfChapter(patch.id());
            int index = indexOf(draft.chapters, Chapter::id, patch.id());
            draft.chapters.set(index, apply(draft.chapters.get(index), patch, Chapter.class, "chapter"));
        }
        return commit(live, work, patchLabel(live, quests, chapters), null);
    }

    public Outcome createQuest(QuestPack live, NewQuest request) {
        Chapter chapter = live.chapters().stream()
                .filter(c -> c.id().equals(request.chapter()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown chapter."));
        Work work = work(live);
        Draft target = work.holderOfChapterOrNull(chapter.id());
        if (target == null) target = work.draftFor(editorFile());
        String title = request.title() == null || request.title().isBlank()
                ? NEW_QUEST
                : request.title().strip();
        Set<String> taken = live.quests().stream().map(Quest::id).collect(Collectors.toSet());
        String id = unique(namespaceOf(chapter.id()), slug(title, "quest"), taken);
        List<String> prerequisites = request.prerequisites() == null ? List.of() : List.copyOf(request.prerequisites());
        target.quests.add(new Quest(
                id,
                chapter.id(),
                title,
                "",
                PLACEHOLDER_ICON,
                request.x(),
                request.y(),
                prerequisites,
                List.of(Objective.simple("task_1", "manual", null, 1, "Mark this quest complete")),
                List.of(),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null));
        return commit(live, work, "Created quest " + title, id);
    }

    public Outcome createChapter(QuestPack live, NewChapter request) {
        Work work = work(live);
        String title = request.title() == null || request.title().isBlank()
                ? NEW_CHAPTER
                : request.title().strip();
        String icon = request.icon() == null || request.icon().isBlank() ? PLACEHOLDER_ICON : request.icon();
        Set<String> taken = live.chapters().stream().map(Chapter::id).collect(Collectors.toSet());
        String id = unique(configuredNamespace(), slug(title, "chapter"), taken);
        int order = live.chapters().stream().mapToInt(Chapter::order).max().orElse(-1) + 1;
        work.draftFor(editorFile()).chapters.add(new Chapter(id, title, icon, order, null, null, null, null));
        return commit(live, work, "Created chapter " + title, id);
    }

    /** Deletes quests and chapters (with the quests inside them) and removes them from every prerequisite list. */
    public Outcome delete(QuestPack live, DeleteRequest request) {
        List<String> chapterIds = request.chapters() == null ? List.of() : request.chapters();
        Set<String> questIds = new LinkedHashSet<>(request.quests() == null ? List.of() : request.quests());
        if (questIds.isEmpty() && chapterIds.isEmpty()) throw new IllegalArgumentException("Nothing to delete.");
        for (Quest q : live.quests()) if (chapterIds.contains(q.chapter())) questIds.add(q.id());
        Work work = work(live);
        for (String id : questIds) work.holderOfQuest(id).quests.removeIf(q -> q.id().equals(id));
        for (String id : chapterIds) work.holderOfChapter(id).chapters.removeIf(c -> c.id().equals(id));
        for (Draft draft : work.drafts.values()) {
            draft.quests.replaceAll(q ->
                    q.prerequisites() == null || q.prerequisites().stream().noneMatch(questIds::contains)
                            ? q
                            : with(
                                    q,
                                    "prerequisites",
                                    GSON.toJsonTree(q.prerequisites().stream()
                                            .filter(p -> !questIds.contains(p))
                                            .toList()),
                                    Quest.class));
            draft.chapters.replaceAll(c -> c.prerequisitesOrEmpty().stream().noneMatch(questIds::contains)
                    ? c
                    : with(
                            c,
                            "prerequisites",
                            GSON.toJsonTree(c.prerequisitesOrEmpty().stream()
                                    .filter(p -> !questIds.contains(p))
                                    .toList()),
                            Chapter.class));
        }
        String label = chapterIds.isEmpty()
                ? "Deleted " + count(questIds.size(), "quest")
                : "Deleted " + count(chapterIds.size(), "chapter") + " and " + count(questIds.size(), "quest");
        return commit(live, work, label, null);
    }

    public Outcome undo(QuestPack live) {
        Step step = undoSteps.peekFirst();
        if (step == null) throw new IllegalArgumentException("Nothing to undo.");
        requireUnchanged(step.after());
        Outcome outcome = replay(live, step.before(), "Undid: " + step.label());
        undoSteps.removeFirst();
        redoSteps.addFirst(step);
        return outcome;
    }

    public Outcome redo(QuestPack live) {
        Step step = redoSteps.peekFirst();
        if (step == null) throw new IllegalArgumentException("Nothing to redo.");
        requireUnchanged(step.before());
        Outcome outcome = replay(live, step.after(), "Redid: " + step.label());
        redoSteps.removeFirst();
        undoSteps.addFirst(step);
        return outcome;
    }

    // ---------------------------------------------------------------- working copy

    /** Undo must not overwrite changes made outside the editor since this step. */
    private static void requireUnchanged(Map<Path, String> expected) {
        for (var entry : expected.entrySet()) {
            if (!Objects.equals(read(entry.getKey()), entry.getValue())) {
                throw new IllegalArgumentException(
                        "Cannot undo or redo because " + entry.getKey().getFileName()
                                + " changed outside the editor. Reload quests before continuing.");
            }
        }
    }

    /** The editable files as they are now, as mutable drafts. */
    private final class Work {
        final QuestPack live;
        final List<Source> before;
        final Map<Path, Draft> drafts = new TreeMap<>();

        Work(QuestPack live, List<Source> before) {
            this.live = live;
            this.before = before;
            for (Source source : before) drafts.put(source.path(), new Draft(source.pack()));
        }

        Draft draftFor(Path path) {
            return drafts.computeIfAbsent(path, p -> new Draft(new QuestPack(List.of(), List.of())));
        }

        Draft holderOfQuest(String id) {
            for (Draft draft : drafts.values())
                if (draft.quests.stream().anyMatch(q -> q.id().equals(id))) return draft;
            if (live.quests().stream().noneMatch(q -> q.id().equals(id)))
                throw new IllegalArgumentException("Unknown quest " + id + ".");
            throw new IllegalArgumentException(notEditable("Quest " + id));
        }

        Draft holderOfChapter(String id) {
            Draft draft = holderOfChapterOrNull(id);
            if (draft != null) return draft;
            if (live.chapters().stream().noneMatch(c -> c.id().equals(id)))
                throw new IllegalArgumentException("Unknown chapter " + id + ".");
            throw new IllegalArgumentException(notEditable("Chapter " + id));
        }

        Draft holderOfChapterOrNull(String id) {
            for (Draft draft : drafts.values())
                if (draft.chapters.stream().anyMatch(c -> c.id().equals(id))) return draft;
            return null;
        }
    }

    private Work work(QuestPack live) {
        try {
            return new Work(live, QuestFiles.loadSources(directory));
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read the quest files: " + error.getMessage(), error);
        }
    }

    private static String notEditable(String what) {
        return what
                + " comes from bundled content or a data pack, so it is read-only. Only quests in config/iwantquests/quests can be edited.";
    }

    // ---------------------------------------------------------------- commit

    /** Turns the changed drafts into file contents, validates the merged result, writes it and records the undo step. */
    private Outcome commit(QuestPack live, Work work, String label, String select) {
        Map<Path, String> after = new LinkedHashMap<>();
        Map<Path, QuestPack> original = new HashMap<>();
        for (Source source : work.before) original.put(source.path(), source.pack());
        for (Map.Entry<Path, Draft> entry : work.drafts.entrySet()) {
            QuestPack changed = entry.getValue().pack();
            if (!changed.equals(original.get(entry.getKey()))) after.put(entry.getKey(), QuestFiles.toJson(changed));
        }
        if (after.isEmpty()) throw new IllegalArgumentException("Nothing changed.");
        Map<Path, String> before = new LinkedHashMap<>();
        for (Path path : after.keySet()) before.put(path, read(path));
        Outcome outcome = write(live, work.before, after, label, select);
        undoSteps.addFirst(new Step(label, before, after));
        while (undoSteps.size() > HISTORY) undoSteps.removeLast();
        redoSteps.clear();
        return outcome;
    }

    private Outcome replay(QuestPack live, Map<Path, String> contents, String label) {
        try {
            return write(live, QuestFiles.loadSources(directory), contents, label, null);
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read the quest files: " + error.getMessage(), error);
        }
    }

    private Outcome write(
            QuestPack live, List<Source> current, Map<Path, String> contents, String label, String select) {
        Map<Path, QuestPack> files = new TreeMap<>();
        for (Source source : current) files.put(source.path(), source.pack());
        for (Map.Entry<Path, String> entry : contents.entrySet()) {
            if (entry.getValue() == null) files.remove(entry.getKey());
            else files.put(entry.getKey(), QuestFiles.read(new StringReader(entry.getValue())));
        }
        QuestPack merged = merge(live, current, files.values());
        QuestCatalog catalog = validator.apply(merged);
        Map<Path, String> restore = new LinkedHashMap<>();
        try {
            for (Map.Entry<Path, String> entry : contents.entrySet()) {
                restore.put(entry.getKey(), read(entry.getKey()));
                backup(entry.getKey());
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                else QuestFiles.write(entry.getKey(), entry.getValue());
            }
        } catch (IOException error) {
            restore.forEach((path, text) -> {
                try {
                    if (text == null) Files.deleteIfExists(path);
                    else QuestFiles.write(path, text);
                } catch (IOException ignored) {
                    // Best effort: the original error below is the one worth reporting.
                }
            });
            throw new IllegalArgumentException("Could not save the quest files: " + error.getMessage(), error);
        }
        return new Outcome(merged, catalog, label, select);
    }

    /** Everything the config files defined is replaced by what they define now; other content is untouched. */
    private static QuestPack merge(QuestPack live, List<Source> before, Collection<QuestPack> after) {
        Set<String> oldChapters = new HashSet<>(), oldQuests = new HashSet<>();
        for (Source source : before) {
            source.pack().chapters().forEach(c -> oldChapters.add(c.id()));
            source.pack().quests().forEach(q -> oldQuests.add(q.id()));
        }
        List<Chapter> chapters = new ArrayList<>();
        List<Quest> quests = new ArrayList<>();
        live.chapters().stream().filter(c -> !oldChapters.contains(c.id())).forEach(chapters::add);
        live.quests().stream().filter(q -> !oldQuests.contains(q.id())).forEach(quests::add);
        for (QuestPack pack : after) {
            chapters.addAll(pack.chapters());
            quests.addAll(pack.quests());
        }
        return new QuestPack(List.copyOf(chapters), List.copyOf(quests));
    }

    private static String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read " + path + ": " + error.getMessage(), error);
        }
    }

    /** Copies a file that is about to be replaced into the backup folder and keeps only the newest copies. */
    private void backup(Path file) throws IOException {
        if (!Files.exists(file)) return;
        Files.createDirectories(backups);
        Path copy = Files.createTempFile(
                backups, file.getFileName() + "." + LocalDateTime.now().format(STAMP) + ".", ".json");
        Files.copy(file, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (var stream = Files.list(backups)) {
            List<Path> old = stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(QuestEditor::modified)
                            .thenComparing(p -> p.getFileName().toString())
                            .reversed())
                    .skip(BACKUPS)
                    .toList();
            for (Path path : old) Files.deleteIfExists(path);
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- field patches

    private static <T> T apply(T original, Patch patch, Class<T> type, String kind) {
        if (patch.set() == null || patch.set().isEmpty())
            throw new IllegalArgumentException("Nothing to change on " + patch.id() + ".");
        T result = original;
        for (Map.Entry<String, JsonElement> field : patch.set().entrySet())
            result = with(result, field.getKey(), field.getValue(), type);
        return result;
    }

    /** Sets one field by name through its JSON form, so new record components become editable without extra code. */
    private static <T> T with(T value, String field, JsonElement newValue, Class<T> type) {
        if (field.equals("id"))
            throw new IllegalArgumentException(
                    "An ID cannot be changed, because progress and claims are stored by ID.");
        boolean known = Arrays.stream(type.getRecordComponents())
                .anyMatch(c -> c.getName().equals(field));
        if (!known) throw new IllegalArgumentException("Unknown field: " + field);
        JsonObject json = GSON.toJsonTree(value).getAsJsonObject();
        if (newValue == null || newValue.isJsonNull()) json.remove(field);
        else json.add(field, newValue);
        try {
            return GSON.fromJson(json, type);
        } catch (JsonParseException | IllegalArgumentException error) {
            // Gson reports some bad values (such as text where a number belongs) as a plain NumberFormatException.
            throw new IllegalArgumentException("Invalid value for " + field + ".", error);
        }
    }

    private static <T> int indexOf(List<T> list, Function<T, String> id, String wanted) {
        for (int i = 0; i < list.size(); i++) if (id.apply(list.get(i)).equals(wanted)) return i;
        throw new IllegalArgumentException("Unknown id " + wanted + ".");
    }

    private static String patchLabel(QuestPack live, List<Patch> quests, List<Patch> chapters) {
        boolean onlyMoves = chapters.isEmpty()
                && quests.stream()
                        .allMatch(p -> Set.of("x", "y").containsAll(p.set().keySet()));
        if (onlyMoves) return "Moved " + count(quests.size(), "quest");
        if (quests.size() + chapters.size() > 1) return "Edited " + count(quests.size() + chapters.size(), "item");
        if (!quests.isEmpty()) {
            return "Edited "
                    + live.quests().stream()
                            .filter(q -> q.id().equals(quests.getFirst().id()))
                            .map(Quest::title)
                            .findFirst()
                            .orElse("quest");
        }
        return "Edited "
                + live.chapters().stream()
                        .filter(c -> c.id().equals(chapters.getFirst().id()))
                        .map(Chapter::title)
                        .findFirst()
                        .orElse("chapter");
    }

    private static String count(int amount, String noun) {
        return amount + " " + noun + (amount == 1 ? "" : "s");
    }

    // ---------------------------------------------------------------- IDs

    private String configuredNamespace() {
        String configured = namespace.get();
        return configured == null || !configured.matches("[a-z0-9_.-]+") ? "custom" : configured;
    }

    private static String namespaceOf(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    static String slug(String text, String fallback) {
        String slug =
                text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        return slug.isEmpty() ? fallback : slug;
    }

    private static String unique(String namespace, String slug, Set<String> taken) {
        String id = namespace + ":" + slug;
        for (int n = 2; taken.contains(id); n++) id = namespace + ":" + slug + "_" + n;
        return id;
    }
}
