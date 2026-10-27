package dev.iwantquests.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.iwantquests.objective.ObjectiveRegistry.Contribution;
import dev.iwantquests.progress.ProgressState;
import dev.iwantquests.progress.ProgressState.*;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

class QuestContributionTest {
    private static final Gson JSON = new Gson();
    private QuestPack pack;
    private ProgressState state;
    private QuestEngine engine;
    private UUID a, b;

    @BeforeEach
    void setup() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/data/iwantquests/iwantquests/quests/a_new_beginning.json")))) {
            pack = JSON.fromJson(reader, QuestPack.class);
        }
        state = new ProgressState();
        engine = new QuestEngine(state, catalog(pack));
        a = UUID.randomUUID();
        b = UUID.randomUUID();
    }

    private QuestCatalog catalog(QuestPack p) {
        return new QuestCatalog(p, (kind, target) -> {
            if (target == null || !target.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid target " + target);
        });
    }

    private void event(UUID p, String type, String target, int count) {
        engine.contribute(p, Contribution.item(type, Set.of(target), count));
    }

    private void team() {
        engine.teams().create(a, "Explorers");
        engine.teams().invite(a, b, 1000);
        engine.teams().accept(b, 1001);
    }

    private Team team(UUID player) {
        return engine.teams().team(player);
    }

    private Quest first() {
        return pack.quests().getFirst();
    }

    @Test
    void teammatesGetSeparateContributionTotals() {
        team();
        event(a, "obtain", "#minecraft:logs", 8);
        event(b, "obtain", "#minecraft:logs", 5);
        assertEquals(Map.of(a, 8, b, 5), engine.contributions(team(a), first()));
    }

    @Test
    void contributionsStopAtObjectiveCount() {
        team();
        event(a, "obtain", "#minecraft:logs", 8);
        event(b, "obtain", "#minecraft:logs", 12);
        assertEquals(Map.of(a, 8, b, 8), engine.contributions(team(a), first()));
        // Quest is complete, so further gathering must not inflate anyone's total.
        event(a, "obtain", "#minecraft:logs", 5);
        assertEquals(Map.of(a, 8, b, 8), engine.contributions(team(a), first()));
    }

    @Test
    void teamCreatedFromSoloProgressKeepsContributions() {
        event(a, "obtain", "#minecraft:logs", 6);
        engine.teams().create(a, "Explorers");
        engine.teams().invite(a, b, 1000);
        engine.teams().accept(b, 1001);
        event(b, "obtain", "#minecraft:logs", 4);
        assertEquals(Map.of(a, 6, b, 4), engine.contributions(team(a), first()));
    }

    @Test
    void countersWithoutContributionMapDoNotCrash() {
        team();
        event(a, "obtain", "#minecraft:logs", 4);
        Objective objective = first().objectives().getFirst();
        team(a).quests.get(first().id()).objectives.get(objective.id()).contributions = null;
        assertEquals(Map.of(), engine.contributions(team(a), first()));
        event(a, "obtain", "#minecraft:logs", 4);
        assertEquals(8, engine.count(team(a), first(), objective));
        assertEquals(Map.of(a, 4), engine.contributions(team(a), first()));
    }
}
