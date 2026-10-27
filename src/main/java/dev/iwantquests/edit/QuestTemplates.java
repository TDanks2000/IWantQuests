package dev.iwantquests.edit;

import com.google.gson.Gson;
import dev.iwantquests.quest.QuestFiles;
import dev.iwantquests.quest.QuestPack.Quest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-owned reusable selections, shared between authorised creators on this installation. */
public final class QuestTemplates {
    public record Template(String id, String name, List<Quest> quests) {}

    public record Request(String name, List<String> quests) {}

    private static final Gson JSON = new Gson();
    private final Path directory;

    public record Delete(String id) {}

    public QuestTemplates(Path directory) {
        this.directory = directory;
    }

    public void delete(String id) {
        if (id == null || !UUID.fromString(id).toString().equals(id))
            throw new IllegalArgumentException("Invalid template ID.");
        try {
            Files.deleteIfExists(directory.resolve(id + ".json"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not delete template.", e);
        }
    }

    public List<Template> list() {
        try {
            Files.createDirectories(directory);
            List<Template> result = new ArrayList<>();
            try (var paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .toList()) {
                    try {
                        Template value = JSON.fromJson(Files.readString(path), Template.class);
                        if (value == null
                                || value.id() == null
                                || value.name() == null
                                || value.quests() == null
                                || value.quests().isEmpty()
                                || value.quests().size() > 128
                                || !path.getFileName().toString().equals(UUID.fromString(value.id()) + ".json")
                                || value.quests().stream()
                                        .anyMatch(q -> q == null || q.id() == null || q.title() == null))
                            throw new IllegalArgumentException("Invalid quest template");
                        result.add(value);
                    } catch (RuntimeException invalid) {
                        System.getLogger("iwantquests")
                                .log(
                                        System.Logger.Level.WARNING,
                                        "Skipping invalid quest template " + path.getFileName(),
                                        invalid);
                    }
                }
            }
            return result.stream()
                    .sorted(Comparator.comparing(Template::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read quest templates.", e);
        }
    }

    public void save(Request request, List<Quest> available) {
        if (request.quests() == null
                || request.quests().isEmpty()
                || request.quests().size() > 128)
            throw new IllegalArgumentException("Select 1–128 quests for the template.");
        List<Quest> selected = request.quests().stream()
                .distinct()
                .map(id -> available.stream()
                        .filter(q -> q.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown quest " + id)))
                .toList();
        String name = request.name() == null || request.name().isBlank()
                ? selected.getFirst().title()
                : request.name().strip();
        if (name.length() > 100) throw new IllegalArgumentException("Template name is too long.");
        String id = UUID.randomUUID().toString();
        String text = JSON.toJson(new Template(id, name, selected));
        List<Template> saved = list();
        int bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > 60_000
                || saved.size() >= 64
                || JSON.toJson(saved).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + bytes > 500_000)
            throw new IllegalArgumentException(
                    "Template library is full or this selection is too large. Remove unused template files or copy a smaller group.");
        try {
            QuestFiles.write(directory.resolve(id + ".json"), text);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not save template.", e);
        }
    }
}
