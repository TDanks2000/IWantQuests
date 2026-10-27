package dev.iwantquests.engine;

import dev.iwantquests.objective.ObjectiveRegistry;
import dev.iwantquests.objective.ObjectiveRegistry.Contribution;
import dev.iwantquests.progress.ProgressState;
import dev.iwantquests.progress.ProgressState.*;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import dev.iwantquests.team.TeamService;
import java.util.*;

/** Called exclusively on the server thread; independent of Minecraft for deterministic tests. */
public final class QuestEngine {
    private final ProgressState state;
    private final TeamService teams;
    private QuestCatalog catalog;

    public interface RewardDelivery {
        boolean canDeliver(List<Reward> rewards);

        void deliver(List<Reward> rewards);
    }

    public QuestEngine(ProgressState state, QuestCatalog catalog) {
        this.state = state;
        teams = new TeamService(state);
        this.catalog = catalog;
        reconcile();
    }

    public TeamService teams() {
        return teams;
    }

    public QuestCatalog catalog() {
        return catalog;
    }

    public void reload(QuestCatalog catalog) {
        this.catalog = catalog;
        reconcile();
    }

    public boolean available(Team team, Quest quest) {
        return quest.prerequisites().stream().allMatch(id -> completed(team, id));
    }

    public boolean completed(Team team, String id) {
        QuestProgress progress = team.quests.get(id);
        return progress != null && progress.completed;
    }

    public int count(Team team, Quest quest, Objective objective) {
        QuestProgress progress = team.quests.get(quest.id());
        Counter counter = progress == null ? null : progress.objectives.get(objective.id());
        if (counter == null || !objective.signature().equals(counter.signature)) return 0;
        if (objective.type().equals("possess") && !completed(team, quest.id())) {
            return (int) Math.min(
                    objective.count(),
                    counter.possessions.entrySet().stream()
                            .filter(e -> team.members.contains(e.getKey()))
                            .mapToLong(Map.Entry::getValue)
                            .sum());
        }
        return Math.min(counter.value, objective.count());
    }

    public Set<String> contribute(UUID player, Contribution event) {
        Team team = teams.team(player);
        Set<String> changed = new LinkedHashSet<>();
        // Snapshot availability so one event cannot spill into newly unlocked successors.
        List<Quest> active = catalog.matching(event.type()).stream()
                .filter(q -> !completed(team, q.id()) && (q.retroactive() || available(team, q)))
                .toList();
        for (Quest q : active) {
            for (Objective o : q.objectives()) {
                if (!o.type().equals(event.type())
                        || !ObjectiveRegistry.get(o.type()).matches(o, event)) continue;
                int before = count(team, q, o);
                QuestProgress progress = team.quests.computeIfAbsent(q.id(), id -> new QuestProgress());
                Counter counter = progress.objectives.computeIfAbsent(o.id(), id -> new Counter(o.signature(), 0));
                int after;
                if (o.type().equals("possess")) {
                    Integer old = counter.possessions.put(player, Math.max(0, event.count()));
                    long total = counter.possessions.entrySet().stream()
                            .filter(e -> team.members.contains(e.getKey()))
                            .mapToLong(Map.Entry::getValue)
                            .sum();
                    after = (int) Math.min(o.count(), total);
                    if (!Objects.equals(old, event.count())) changed.add(q.id());
                } else after = (int) Math.min(o.count(), (long) before + Math.max(0, event.count()));
                if (before == after) continue;
                // Possession is a snapshot of holdings, so only accumulating objectives credit contributors.
                if (!o.type().equals("possess"))
                    ProgressState.contributions(counter).merge(player, after - before, Integer::sum);
                counter.value = after;
                changed.add(q.id());
            }
        }
        // Retroactive objectives may already be ready when a prerequisite completes.
        boolean again;
        do {
            again = false;
            for (Quest q : catalog.quests()) {
                if (completed(team, q.id()) || !available(team, q)) continue;
                if (q.objectives().stream().allMatch(o -> count(team, q, o) >= o.count())) {
                    team.quests.computeIfAbsent(q.id(), id -> new QuestProgress()).completed = true;
                    changed.add(q.id());
                    again = true;
                }
            }
        } while (again);
        return changed;
    }
    /** Team members' contributions to a quest, in team member order. Possession uses holdings snapshots instead. */
    public Map<UUID, Integer> contributions(Team team, Quest quest) {
        Map<UUID, Integer> totals = new HashMap<>();
        QuestProgress progress = team.quests.get(quest.id());
        if (progress != null)
            for (Objective o : quest.objectives()) {
                Counter counter = progress.objectives.get(o.id());
                if (counter == null || !o.signature().equals(counter.signature)) continue;
                Map<UUID, Integer> source =
                        o.type().equals("possess") ? counter.possessions : ProgressState.contributions(counter);
                source.forEach((id, amount) -> {
                    if (team.members.contains(id) && amount != null && amount > 0)
                        totals.merge(id, amount, Integer::sum);
                });
            }
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (UUID member : team.members) if (totals.getOrDefault(member, 0) > 0) result.put(member, totals.get(member));
        return result;
    }

    public boolean claimed(UUID player, String quest) {
        Quest definition = catalog.quest(quest);
        if (definition != null && definition.repeatable()) {
            QuestProgress progress = teams.team(player).quests.get(quest);
            return progress != null && progress.claimedBy != null && progress.claimedBy.contains(player);
        }
        return state.claims.getOrDefault(player, Set.of()).contains(quest);
    }

    public Set<String> completeAction(UUID player, String questId, String objectiveId, int amount) {
        Team team = teams.team(player);
        Quest quest = catalog.quest(questId);
        if (quest == null || !available(team, quest) || completed(team, questId))
            throw new IllegalArgumentException("Quest is not available.");
        Objective objective = quest.objectives().stream()
                .filter(o -> o.id().equals(objectiveId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown objective."));
        if (!Set.of("manual", "submit").contains(objective.type()))
            throw new IllegalArgumentException("Not an actionable objective.");
        int before = count(team, quest, objective);
        int after = (int) Math.min(objective.count(), (long) before + Math.max(0, amount));
        QuestProgress progress = team.quests.computeIfAbsent(questId, id -> new QuestProgress());
        Counter counter = progress.objectives.get(objectiveId);
        // Replace only incompatible counters so existing contributions survive a normal submission.
        if (counter == null || !objective.signature().equals(counter.signature)) {
            counter = new Counter(objective.signature(), 0);
            progress.objectives.put(objectiveId, counter);
        }
        counter.value = after;
        if (after > before) ProgressState.contributions(counter).merge(player, after - before, Integer::sum);
        Set<String> changed = new LinkedHashSet<>(contribute(player, Contribution.item("__completion", Set.of(), 0)));
        changed.add(questId);
        return changed;
    }
    /**
     * Claims a quest's rewards for one player.
     * @return true when this claim finished a repeatable quest's round, so the team's progress started over
     */
    public boolean claim(UUID player, String id, RewardDelivery delivery) {
        Quest quest = catalog.quest(id);
        if (quest == null) throw new IllegalArgumentException("Unknown quest.");
        if (!completed(teams.team(player), id))
            throw new IllegalArgumentException("Your team has not completed this quest.");
        if (claimed(player, id)) throw new IllegalArgumentException("You have already claimed this reward.");
        if (!delivery.canDeliver(quest.rewards()))
            throw new IllegalArgumentException("Make room in your inventory before claiming.");
        // Mark first to protect against duplicate/reentrant requests.
        Team team = teams.team(player);
        Set<UUID> round = null;
        if (quest.repeatable()) {
            QuestProgress progress = team.quests.get(id);
            if (progress.claimedBy == null) progress.claimedBy = new LinkedHashSet<>();
            round = progress.claimedBy;
            round.add(player);
        } else state.claims.computeIfAbsent(player, p -> new HashSet<>()).add(id);
        try {
            delivery.deliver(quest.rewards());
        } catch (RuntimeException error) {
            // A failed delivery must not consume the claim; the player can try again.
            if (round != null) round.remove(player);
            else state.claims.get(player).remove(id);
            throw error;
        }
        return quest.repeatable() && startOver(team, quest);
    }
    /** Starts every finished repeatable quest of {@code team} over when all current members have claimed it. */
    public Set<String> restartRepeatables(Team team) {
        Set<String> restarted = new LinkedHashSet<>();
        for (Quest quest : catalog.quests())
            if (quest.repeatable() && startOver(team, quest)) restarted.add(quest.id());
        return restarted;
    }

    private boolean startOver(Team team, Quest quest) {
        QuestProgress progress = team.quests.get(quest.id());
        if (progress == null
                || !progress.completed
                || progress.claimedBy == null
                || !progress.claimedBy.containsAll(team.members)) return false;
        progress.objectives.clear();
        progress.completed = false;
        progress.claimedBy = null;
        progress.rounds++;
        return true;
    }

    private void reconcile() {
        for (Team team : state.teams.values())
            for (Quest q : catalog.quests()) {
                QuestProgress progress = team.quests.get(q.id());
                if (progress == null) continue;
                for (Objective o : q.objectives()) {
                    Counter counter = progress.objectives.get(o.id());
                    if (counter != null && !o.signature().equals(counter.signature)) progress.objectives.remove(o.id());
                }
                progress.completed = q.objectives().stream().allMatch(o -> count(team, q, o) >= o.count());
            }
        // Iterate until no descendant remains completed behind a changed prerequisite.
        boolean changed;
        do {
            changed = false;
            for (Team team : state.teams.values())
                for (Quest q : catalog.quests()) {
                    QuestProgress progress = team.quests.get(q.id());
                    if (progress != null && progress.completed && !available(team, q)) {
                        progress.completed = false;
                        changed = true;
                    }
                }
        } while (changed);
    }
}
