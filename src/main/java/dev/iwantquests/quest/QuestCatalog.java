package dev.iwantquests.quest;

import dev.iwantquests.objective.ObjectiveRegistry;
import dev.iwantquests.quest.QuestPack.*;
import dev.iwantquests.reward.RewardDefinitionRegistry;
import java.util.*;
import java.util.function.BiConsumer;

public final class QuestCatalog {
    /**
     * Objectives a repeatable quest may use. Holding, standing somewhere, visiting or clicking would complete
     * the quest again immediately, and picking items up again is easy to farm by dropping them.
     */
    public static final List<String> REPEATABLE_OBJECTIVES = List.of("craft", "kill", "submit");

    private final QuestPack pack;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Map<String, List<Quest>> byType = new HashMap<>();

    public QuestCatalog(QuestPack pack, BiConsumer<String, String> validateTarget) {
        if (pack == null || pack.chapters() == null || pack.quests() == null) fail("Missing chapters or quests");
        Set<String> chapters = new HashSet<>();
        for (Chapter c : pack.chapters()) {
            id(c.id());
            text(c.title(), "chapter title");
            if (!chapters.add(c.id())) fail("Duplicate chapter " + c.id());
            validateTarget.accept("item", c.icon());
            subtitle(c.subtitle(), c.id());
            if (c.theme() != null) id(c.theme());
            if (c.background() != null) id(c.background());
        }
        for (Quest q : pack.quests()) {
            id(q.id());
            text(q.title(), "quest title");
            if (quests.putIfAbsent(q.id(), q) != null) fail("Duplicate quest " + q.id());
            if (!chapters.contains(q.chapter())) fail(q.id() + ": missing chapter " + q.chapter());
            if (!Double.isFinite(q.x()) || !Double.isFinite(q.y())) fail(q.id() + ": invalid position");
            if (q.icon() == null && q.iconTexture() == null) fail(q.id() + ": icon or iconTexture is required");
            if (q.icon() != null) validateTarget.accept("item", q.icon());
            if (q.iconTexture() != null) id(q.iconTexture());
            subtitle(q.subtitle(), q.id());
            String shape = q.shapeOrDefault();
            if (!List.of("task", "goal", "challenge").contains(shape) && !validId(shape))
                fail(q.id() + ": unknown shape " + shape);
            if (!Double.isFinite(q.size()) || (q.size() != 0 && (q.size() < 0.5 || q.size() > 3.0)))
                fail(q.id() + ": size must be between 0.5 and 3.0");
            if (q.prerequisites() == null
                    || q.objectives() == null
                    || q.objectives().isEmpty()
                    || q.rewards() == null)
                fail(q.id() + ": prerequisites, objectives and rewards arrays are required");
            Set<String> objectiveIds = new HashSet<>();
            Set<String> types = new HashSet<>();
            for (Objective o : q.objectives()) {
                text(o.id(), "objective id");
                text(o.type(), "objective type");
                if (o.id().length() > 64) fail(q.id() + ": objective id exceeds 64 characters");
                if (!objectiveIds.add(o.id())) fail(q.id() + ": duplicate objective " + o.id());
                if (o.count() < 1 || o.count() > 1_000_000) fail(q.id() + ": invalid objective count");
                ObjectiveRegistry.get(o.type()).validate(o);
                if (List.of("obtain", "possess", "craft", "submit").contains(o.type()))
                    validateTarget.accept("item_selector", o.target());
                if (o.type().equals("kill")) validateTarget.accept("entity", o.target());
                if (o.type().equals("dimension")) id(o.target());
                if (o.type().equals("region")) {
                    id(o.dimension());
                    if (!Double.isFinite(o.minX())
                            || !Double.isFinite(o.minY())
                            || !Double.isFinite(o.minZ())
                            || !Double.isFinite(o.maxX())
                            || !Double.isFinite(o.maxY())
                            || !Double.isFinite(o.maxZ())) fail("Non-finite region");
                }
                types.add(o.type());
            }
            for (String type : types)
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(q);
            for (Reward r : q.rewards()) {
                if (r.count() < 0 || r.count() > 1_000_000) fail(q.id() + ": invalid reward count");
                text(r.type(), "reward type");
                try {
                    RewardDefinitionRegistry.validate(r, validateTarget);
                } catch (IllegalArgumentException error) {
                    fail(q.id() + ": " + error.getMessage());
                }
            }
            if (q.repeatable()) {
                if (q.retroactive()) fail(q.id() + ": a repeatable quest cannot be retroactive");
                for (Objective o : q.objectives())
                    if (!REPEATABLE_OBJECTIVES.contains(o.type()))
                        fail(q.id() + ": a repeatable quest can only use " + String.join(", ", REPEATABLE_OBJECTIVES)
                                + " objectives, not " + o.type());
            }
        }
        for (Quest q : quests.values())
            for (String prerequisite : q.prerequisites()) {
                if (!quests.containsKey(prerequisite)) fail(q.id() + ": missing prerequisite " + prerequisite);
                if (quests.get(prerequisite).repeatable())
                    fail(q.id() + ": cannot require repeatable quest " + prerequisite);
            }
        for (Chapter c : pack.chapters())
            for (String prerequisite : c.prerequisitesOrEmpty()) {
                if (!quests.containsKey(prerequisite)) fail(c.id() + ": missing chapter prerequisite " + prerequisite);
                if (quests.get(prerequisite).repeatable())
                    fail(c.id() + ": cannot require repeatable quest " + prerequisite);
            }
        Set<String> visited = new HashSet<>();
        for (String id : quests.keySet()) visit(id, visited, new HashSet<>());
        this.pack = pack;
    }

    private void visit(String id, Set<String> visited, Set<String> path) {
        if (visited.contains(id)) return;
        if (!path.add(id)) fail("Circular prerequisite dependency at " + id);
        for (String prerequisite : quests.get(id).prerequisites()) visit(prerequisite, visited, path);
        path.remove(id);
        visited.add(id);
    }

    public QuestPack pack() {
        return pack;
    }

    public Quest quest(String id) {
        return quests.get(id);
    }

    public Collection<Quest> quests() {
        return quests.values();
    }

    public List<Quest> matching(String type) {
        return byType.getOrDefault(type, List.of());
    }

    private static boolean validId(String id) {
        return id != null && id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static void id(String id) {
        if (!validId(id)) fail("Invalid identifier: " + id);
    }

    private static void subtitle(String value, String owner) {
        if (value != null && value.length() > 128) fail(owner + ": subtitle exceeds 128 characters");
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) fail("Missing " + name);
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
