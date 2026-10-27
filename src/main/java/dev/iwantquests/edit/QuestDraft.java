package dev.iwantquests.edit;

import com.google.gson.Gson;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** A durable private authoring branch. Live files and progression are untouched until publish. */
public final class QuestDraft {
    private static final Gson JSON = new Gson();

    private record Base(QuestPack pack, Map<String, String> files) {}

    public record Review(
            List<String> added,
            List<String> changed,
            List<String> removed,
            List<String> chapters,
            List<String> progressWarnings,
            boolean conflict) {
        public boolean changedAnything() {
            return !added.isEmpty() || !changed.isEmpty() || !removed.isEmpty() || !chapters.isEmpty();
        }
    }

    private final Path live, root, staged;
    private final Function<QuestPack, QuestCatalog> validator;
    private final QuestEditor editor;
    private final QuestEditor publisher;
    private Base base;

    public QuestDraft(
            Path live,
            Path root,
            Path backups,
            Function<QuestPack, QuestCatalog> validator,
            Supplier<String> namespace) {
        this.live = live.toAbsolutePath().normalize();
        this.root = root.toAbsolutePath().normalize();
        if (this.root.startsWith(this.live) || this.live.startsWith(this.root))
            throw new IllegalArgumentException("Private drafts must be separate from published quest files.");
        this.staged = this.root.resolve("quests");
        this.validator = validator;
        editor = new QuestEditor(staged, this.root.resolve("history"), validator, namespace);
        publisher = new QuestEditor(this.live, backups, validator, namespace);
    }

    public QuestEditor editor() {
        return editor;
    }

    public QuestPack pack(QuestPack current) {
        ensure(current);
        List<Chapter> chapters = new ArrayList<>();
        List<Quest> quests = new ArrayList<>();
        Set<String> ownedChapters = new HashSet<>(), ownedQuests = new HashSet<>();
        for (String text : base.files().values()) {
            QuestPack old = QuestFiles.read(new StringReader(text));
            old.chapters().forEach(c -> ownedChapters.add(c.id()));
            old.quests().forEach(q -> ownedQuests.add(q.id()));
        }
        base.pack().chapters().stream()
                .filter(c -> !ownedChapters.contains(c.id()))
                .forEach(chapters::add);
        base.pack().quests().stream().filter(q -> !ownedQuests.contains(q.id())).forEach(quests::add);
        try {
            for (var source : QuestFiles.loadSources(staged)) {
                chapters.addAll(source.pack().chapters());
                quests.addAll(source.pack().quests());
            }
        } catch (IOException e) {
            throw failure(e);
        }
        return validator
                .apply(new QuestPack(List.copyOf(chapters), List.copyOf(quests)))
                .pack();
    }

    private void ensure(QuestPack current) {
        if (base != null) return;
        Path manifest = root.resolve("base.json");
        try {
            if (Files.exists(manifest)) {
                base = JSON.fromJson(Files.readString(manifest), Base.class);
                if (base == null || base.pack() == null || base.files() == null)
                    throw new IllegalArgumentException("Invalid saved draft.");
                return;
            }
            discard(); // Recover an interrupted first snapshot before copying a fresh baseline.
            Map<String, String> files = files(live);
            for (var file : files.entrySet()) QuestFiles.write(safe(staged, file.getKey()), file.getValue());
            Base created = new Base(current, files);
            QuestFiles.write(manifest, JSON.toJson(created));
            base = created;
        } catch (IOException e) {
            throw failure(e);
        }
    }

    public Review review(QuestPack current) {
        QuestPack proposed = pack(current);
        Map<String, Quest> before = index(base.pack().quests(), Quest::id), after = index(proposed.quests(), Quest::id);
        List<String> added = new ArrayList<>(),
                changed = new ArrayList<>(),
                removed = new ArrayList<>(),
                warnings = new ArrayList<>();
        after.forEach((id, q) -> {
            Quest old = before.get(id);
            if (old == null) added.add(id + " — " + q.title());
            else if (!old.equals(q)) {
                var oldJson = JSON.toJsonTree(old).getAsJsonObject();
                var newJson = JSON.toJsonTree(q).getAsJsonObject();
                Set<String> fields = new LinkedHashSet<>(oldJson.keySet());
                fields.addAll(newJson.keySet());
                String difference = fields.stream()
                        .filter(f -> !Objects.equals(oldJson.get(f), newJson.get(f)))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                changed.add(id + " — " + q.title() + " (" + difference + ")");
                if (!Objects.equals(old.objectives(), q.objectives())
                        || !Objects.equals(old.prerequisites(), q.prerequisites()))
                    warnings.add(id + ": objective or requirement changes can reset progress.");
                if (!Objects.equals(old.rewards(), q.rewards()))
                    warnings.add(id + ": rewards changed; existing claims remain claimed.");
            }
        });
        before.forEach((id, q) -> {
            if (!after.containsKey(id)) {
                removed.add(id + " — " + q.title());
                warnings.add(id + ": removing this quest removes its team progress; claim history is retained.");
            }
        });
        Map<String, Chapter> oldChapters = index(base.pack().chapters(), Chapter::id),
                newChapters = index(proposed.chapters(), Chapter::id);
        Set<String> chapterIds = new TreeSet<>(oldChapters.keySet());
        chapterIds.addAll(newChapters.keySet());
        List<String> chapterChanges = chapterIds.stream()
                .filter(id -> !Objects.equals(oldChapters.get(id), newChapters.get(id)))
                .toList();
        return new Review(
                List.copyOf(added),
                List.copyOf(changed),
                List.copyOf(removed),
                chapterChanges,
                List.copyOf(warnings),
                conflicts(current));
    }

    public QuestEditor.Outcome publish(QuestPack current) {
        Review review = review(current);
        if (review.conflict())
            throw new IllegalArgumentException(
                    "Published quests changed since this draft began. Export or copy your work, then discard the draft and start from the latest version.");
        if (!review.changedAnything()) throw new IllegalArgumentException("No draft changes to publish.");
        Map<String, String> desired = files(staged);
        Map<Path, String> updates = new LinkedHashMap<>();
        Set<String> names = new TreeSet<>(base.files().keySet());
        names.addAll(desired.keySet());
        for (String name : names)
            if (!Objects.equals(base.files().get(name), desired.get(name)))
                updates.put(safe(live, name), desired.get(name));
        QuestEditor.Outcome outcome = publisher.replaceFiles(current, updates);
        // A completed publication is safe to resume after a crash: the retained draft becomes stale, never republishes
        // silently.
        try {
            discard();
        } catch (IllegalArgumentException cleanup) {
            return new QuestEditor.Outcome(
                    outcome.pack(),
                    outcome.catalog(),
                    "Published quest draft. Draft cleanup failed; discard the retained draft before further edits.",
                    null);
        }
        return outcome;
    }

    public boolean conflicts(QuestPack current) {
        try {
            return !Objects.equals(base.pack(), current) || !base.files().equals(files(live));
        } catch (IllegalArgumentException unreadable) {
            return true;
        }
    }

    public void discard() {
        try {
            if (Files.exists(root))
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        if (!path.toAbsolutePath().normalize().startsWith(root))
                            throw new IOException("Invalid draft path");
                        Files.delete(path);
                    }
                }
            base = null;
            editor.clearHistory();
        } catch (IOException e) {
            throw failure(e);
        }
    }

    private static Path safe(Path directory, String name) {
        Path result = directory.resolve(name).normalize();
        if (!result.startsWith(directory) || result.equals(directory))
            throw new IllegalArgumentException("Invalid quest file path.");
        return result;
    }

    private static Map<String, String> files(Path directory) {
        Map<String, String> result = new TreeMap<>();
        try {
            for (var source : QuestFiles.loadSources(directory))
                result.put(directory.relativize(source.path()).toString(), Files.readString(source.path()));
        } catch (IOException e) {
            throw failure(e);
        }
        return result;
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        values.forEach(v -> result.put(key.apply(v), v));
        return result;
    }

    private static IllegalArgumentException failure(IOException e) {
        return new IllegalArgumentException("Could not access quest draft: " + e.getMessage(), e);
    }
}
