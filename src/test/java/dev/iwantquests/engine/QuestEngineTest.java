package dev.iwantquests.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.iwantquests.client.ui.CanvasTransform;
import dev.iwantquests.objective.ObjectiveRegistry.Contribution;
import dev.iwantquests.progress.ProgressState;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

class QuestEngineTest {
    private static final Gson JSON = new Gson();
    private QuestPack pack;
    private ProgressState state;
    private QuestEngine engine;
    private UUID a, b;
    private int deliveries;

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

    private QuestEngine.RewardDelivery delivery(boolean space) {
        return new QuestEngine.RewardDelivery() {
            public boolean canDeliver(List<Reward> r) {
                return space;
            }

            public void deliver(List<Reward> r) {
                deliveries++;
            }
        };
    }

    private void event(UUID p, String type, String target, int count) {
        engine.contribute(p, Contribution.item(type, Set.of(target), count));
    }

    private boolean done(String id) {
        return engine.completed(engine.teams().team(a), "iwantquests:" + id);
    }

    private void team() {
        engine.teams().create(a, "Explorers");
        engine.teams().invite(a, b, 1000);
        engine.teams().accept(b, 1001);
    }

    @Test
    void sampleJsonHasFiveConnectedQuests() {
        assertEquals(5, engine.catalog().quests().size());
        assertEquals("A New Beginning", pack.chapters().getFirst().title());
        for (int i = 1; i < 5; i++)
            assertEquals(
                    List.of(pack.quests().get(i - 1).id()), pack.quests().get(i).prerequisites());
    }

    @Test
    void prerequisitesRejectEarlyGameplay() {
        event(a, "craft", "minecraft:wooden_pickaxe", 1);
        event(a, "obtain", "minecraft:cobblestone", 32);
        assertFalse(done("getting_equipped"));
        assertFalse(done("stone_age"));
        event(a, "obtain", "#minecraft:logs", 16);
        assertTrue(done("first_steps"));
        assertFalse(done("getting_equipped"));
        assertTrue(engine.available(engine.teams().team(a), pack.quests().get(1)));
    }

    @Test
    void twoPlayersAggregateAndClaimIndependently() {
        team();
        event(a, "obtain", "#minecraft:logs", 8);
        assertFalse(done("first_steps"));
        event(b, "obtain", "#minecraft:logs", 8);
        assertTrue(done("first_steps"));
        assertEquals(engine.teams().team(a).id, engine.teams().team(b).id);
        engine.claim(a, pack.quests().getFirst().id(), delivery(true));
        engine.claim(b, pack.quests().getFirst().id(), delivery(true));
        assertEquals(2, deliveries);
        assertTrue(engine.claimed(a, pack.quests().getFirst().id()));
        assertTrue(engine.claimed(b, pack.quests().getFirst().id()));
    }

    @Test
    void repeatedRequestsAndEventsCannotDuplicateRewards() {
        event(a, "obtain", "#minecraft:logs", 16);
        String id = pack.quests().getFirst().id();
        engine.claim(a, id, delivery(true));
        for (int i = 0; i < 10; i++) {
            event(a, "obtain", "#minecraft:logs", 16);
            assertThrows(IllegalArgumentException.class, () -> engine.claim(a, id, delivery(true)));
        }
        assertEquals(1, deliveries);
    }

    @Test
    void lockedAndFullInventoryClaimsAreRejectedWithoutRecordingClaim() {
        String id = pack.quests().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> engine.claim(a, id, delivery(true)));
        event(a, "obtain", "#minecraft:logs", 16);
        assertThrows(IllegalArgumentException.class, () -> engine.claim(a, id, delivery(false)));
        assertFalse(engine.claimed(a, id));
        assertEquals(0, deliveries);
        engine.claim(a, id, delivery(true));
        assertEquals(1, deliveries);
    }

    @Test
    void failedDeliveryDoesNotConsumeTheClaim() {
        event(a, "obtain", "#minecraft:logs", 16);
        String id = pack.quests().getFirst().id();
        var broken = new QuestEngine.RewardDelivery() {
            public boolean canDeliver(List<Reward> r) {
                return true;
            }

            public void deliver(List<Reward> r) {
                throw new IllegalStateException("boom");
            }
        };
        assertThrows(IllegalStateException.class, () -> engine.claim(a, id, broken));
        assertFalse(engine.claimed(a, id));
        engine.claim(a, id, delivery(true));
        assertTrue(engine.claimed(a, id));
        assertEquals(1, deliveries);
    }
    // ---------------------------------------------------------------- repeatable quests
    private QuestPack repeatablePack() {
        Quest base = pack.quests().getFirst();
        Quest farm = new Quest(
                "iwantquests:farm",
                base.chapter(),
                "Farm",
                "",
                base.icon(),
                0,
                0,
                List.of(),
                List.of(Objective.simple("main", "kill", "minecraft:zombie", 3, "")),
                List.of(new Reward("xp", null, 5)),
                false,
                false,
                false,
                true,
                null,
                null,
                0,
                null);
        return withQuests(List.of(farm));
    }

    private boolean farmDone(UUID p) {
        return engine.completed(engine.teams().team(p), "iwantquests:farm");
    }

    @Test
    void soloRepeatableQuestStartsOverAtOnce() {
        engine.reload(catalog(repeatablePack()));
        for (int round = 1; round <= 3; round++) {
            event(a, "kill", "minecraft:zombie", 3);
            assertTrue(farmDone(a));
            assertFalse(engine.claimed(a, "iwantquests:farm"));
            assertTrue(engine.claim(a, "iwantquests:farm", delivery(true)));
            assertFalse(farmDone(a));
            assertFalse(engine.claimed(a, "iwantquests:farm"));
            assertEquals(round, engine.teams().team(a).quests.get("iwantquests:farm").rounds);
        }
        assertEquals(3, deliveries);
        assertTrue(state.claims.getOrDefault(a, Set.of()).isEmpty(), "repeat claims are kept per team, not per player");
    }

    @Test
    void teamRepeatableWaitsForEveryMemberToClaim() {
        engine.reload(catalog(repeatablePack()));
        team();
        event(a, "kill", "minecraft:zombie", 2);
        event(b, "kill", "minecraft:zombie", 1);
        assertFalse(engine.claim(a, "iwantquests:farm", delivery(true)));
        assertTrue(farmDone(a));
        assertTrue(engine.claimed(a, "iwantquests:farm"));
        assertFalse(engine.claimed(b, "iwantquests:farm"));
        assertThrows(IllegalArgumentException.class, () -> engine.claim(a, "iwantquests:farm", delivery(true)));
        event(a, "kill", "minecraft:zombie", 5);
        var farm = engine.catalog().quest("iwantquests:farm");
        assertEquals(
                3,
                engine.count(engine.teams().team(a), farm, farm.objectives().getFirst()),
                "a finished round takes no more progress");
        assertTrue(engine.claim(b, "iwantquests:farm", delivery(true)));
        assertFalse(farmDone(a));
        assertFalse(engine.claimed(a, "iwantquests:farm"));
        assertFalse(engine.claimed(b, "iwantquests:farm"));
        event(b, "kill", "minecraft:zombie", 3);
        assertTrue(farmDone(a));
        engine.claim(a, "iwantquests:farm", delivery(true));
        engine.claim(b, "iwantquests:farm", delivery(true));
        assertEquals(4, deliveries);
    }

    @Test
    void repeatableRestartsWhenTheLastUnclaimedMemberLeaves() {
        engine.reload(catalog(repeatablePack()));
        team();
        event(a, "kill", "minecraft:zombie", 3);
        engine.claim(a, "iwantquests:farm", delivery(true));
        assertTrue(farmDone(a));
        var team = engine.teams().team(a);
        engine.teams().leave(b);
        assertEquals(Set.of("iwantquests:farm"), engine.restartRepeatables(team));
        assertFalse(farmDone(a));
        assertTrue(engine.restartRepeatables(team).isEmpty());
    }

    @Test
    void repeatableProgressSurvivesASaveAndLoad() {
        engine.reload(catalog(repeatablePack()));
        team();
        event(a, "kill", "minecraft:zombie", 3);
        engine.claim(a, "iwantquests:farm", delivery(true));
        ProgressState loaded = JSON.fromJson(JSON.toJson(state), ProgressState.class);
        QuestEngine again = new QuestEngine(loaded, catalog(repeatablePack()));
        assertTrue(again.completed(again.teams().team(a), "iwantquests:farm"));
        assertTrue(again.claimed(a, "iwantquests:farm"));
        assertFalse(again.claimed(b, "iwantquests:farm"));
        assertTrue(again.claim(b, "iwantquests:farm", delivery(true)));
        assertFalse(again.completed(again.teams().team(a), "iwantquests:farm"));
    }

    @Test
    void failedDeliveryOfARepeatableDoesNotConsumeTheClaim() {
        engine.reload(catalog(repeatablePack()));
        event(a, "kill", "minecraft:zombie", 3);
        var broken = new QuestEngine.RewardDelivery() {
            public boolean canDeliver(List<Reward> r) {
                return true;
            }

            public void deliver(List<Reward> r) {
                throw new IllegalStateException("boom");
            }
        };
        assertThrows(IllegalStateException.class, () -> engine.claim(a, "iwantquests:farm", broken));
        assertFalse(engine.claimed(a, "iwantquests:farm"));
        assertTrue(farmDone(a));
        assertTrue(engine.claim(a, "iwantquests:farm", delivery(true)));
    }

    @Test
    void repeatableDefinitionsAreRestricted() {
        Quest farm = repeatablePack().quests().getFirst();
        Quest obtain = copy(
                farm,
                List.of(),
                List.of(Objective.simple("main", "obtain", "minecraft:stone", 1, "")),
                farm.rewards(),
                false);
        Quest loop = new Quest(
                obtain.id(),
                obtain.chapter(),
                obtain.title(),
                "",
                obtain.icon(),
                0,
                0,
                List.of(),
                obtain.objectives(),
                obtain.rewards(),
                false,
                false,
                false,
                true,
                null,
                null,
                0,
                null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(loop))))
                .getMessage()
                .contains("can only use"));
        Quest retro = new Quest(
                farm.id(),
                farm.chapter(),
                farm.title(),
                "",
                farm.icon(),
                0,
                0,
                List.of(),
                farm.objectives(),
                farm.rewards(),
                false,
                false,
                true,
                true,
                null,
                null,
                0,
                null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(retro))))
                .getMessage()
                .contains("retroactive"));
        Quest dependent = new Quest(
                "iwantquests:after",
                farm.chapter(),
                "After",
                "",
                farm.icon(),
                0,
                0,
                List.of(farm.id()),
                farm.objectives(),
                farm.rewards(),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(farm, dependent))))
                .getMessage()
                .contains("cannot require repeatable"));
        assertDoesNotThrow(() -> catalog(withQuests(List.of(farm))));
        for (String type : List.of("possess", "dimension", "region", "manual", "obtain")) {
            assertFalse(QuestCatalog.REPEATABLE_OBJECTIVES.contains(type), type);
        }
    }

    @Test
    void teamSwitchesPreservePlayerWideClaims() {
        team();
        event(a, "obtain", "#minecraft:logs", 16);
        String id = pack.quests().getFirst().id();
        engine.claim(b, id, delivery(true));
        engine.teams().leave(b);
        engine.teams().create(b, "Another team");
        event(b, "obtain", "#minecraft:logs", 16);
        assertThrows(IllegalArgumentException.class, () -> engine.claim(b, id, delivery(true)));
        assertEquals(1, deliveries);
    }

    @Test
    void separateTeamsHaveSeparateProgress() {
        engine.teams().create(a, "One");
        engine.teams().create(b, "Two");
        event(a, "obtain", "#minecraft:logs", 16);
        assertTrue(done("first_steps"));
        assertFalse(engine.completed(
                engine.teams().team(b), pack.quests().getFirst().id()));
    }

    @Test
    void roundTripRestoresTeamsProgressClaimsAndReconnectState() {
        team();
        event(a, "obtain", "#minecraft:logs", 8);
        state = JSON.fromJson(JSON.toJson(state), ProgressState.class);
        engine = new QuestEngine(state, catalog(pack));
        event(b, "obtain", "#minecraft:logs", 8);
        assertTrue(done("first_steps"));
        engine.claim(a, pack.quests().getFirst().id(), delivery(true));
        state = JSON.fromJson(JSON.toJson(state), ProgressState.class);
        engine = new QuestEngine(state, catalog(pack));
        assertEquals(engine.teams().team(a).id, engine.teams().team(b).id);
        assertTrue(done("first_steps"));
        assertTrue(engine.claimed(a, pack.quests().getFirst().id()));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.claim(a, pack.quests().getFirst().id(), delivery(true)));
        engine.claim(b, pack.quests().getFirst().id(), delivery(true));
        assertEquals(2, deliveries);
    }

    @Test
    void completeGameplayLoop() {
        team();
        event(a, "obtain", "#minecraft:logs", 16);
        event(b, "craft", "minecraft:wooden_pickaxe", 1);
        event(a, "obtain", "minecraft:cobblestone", 20);
        event(b, "obtain", "minecraft:cobblestone", 12);
        event(a, "craft", "#minecraft:beds", 1);
        event(b, "obtain", "minecraft:iron_pickaxe", 1);
        for (Quest q : pack.quests()) {
            assertTrue(engine.completed(engine.teams().team(a), q.id()));
            engine.claim(a, q.id(), delivery(true));
            engine.claim(b, q.id(), delivery(true));
        }
        assertEquals(10, deliveries);
    }

    private Quest copy(
            Quest q,
            List<String> prerequisites,
            List<Objective> objectives,
            List<Reward> rewards,
            boolean retroactive) {
        return new Quest(
                q.id(),
                q.chapter(),
                q.title(),
                q.description(),
                q.icon(),
                q.x(),
                q.y(),
                prerequisites,
                objectives,
                rewards,
                q.optional(),
                q.hidden(),
                retroactive,
                false,
                q.subtitle(),
                q.shape(),
                q.size(),
                q.iconTexture());
    }

    private QuestPack withQuests(List<Quest> quests) {
        return new QuestPack(pack.chapters(), quests);
    }

    @Test
    void invalidDefinitionsReportUsefulErrors() {
        Quest q = pack.quests().getFirst();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(q, q))))
                .getMessage()
                .contains("Duplicate quest"));
        Quest missing = copy(q, List.of("iwantquests:missing"), q.objectives(), q.rewards(), false);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(missing))))
                .getMessage()
                .contains("missing prerequisite"));
        Quest cycle = copy(q, List.of(q.id()), q.objectives(), q.rewards(), false);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog(withQuests(List.of(cycle))))
                .getMessage()
                .contains("Circular"));
        Objective unknown = new Objective("bad", "unknown", "minecraft:stone", 1, "", null, 0, 0, 0, 0, 0, 0);
        assertTrue(assertThrows(
                        IllegalArgumentException.class,
                        () -> catalog(withQuests(List.of(copy(q, List.of(), List.of(unknown), q.rewards(), false)))))
                .getMessage()
                .contains("Unknown objective"));
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog(withQuests(List.of(copy(
                        q, List.of(), q.objectives(), List.of(new Reward("item", "minecraft:stone", 0)), false)))));
    }

    @Test
    void changedObjectiveDoesNotReuseIncompatibleProgressOrResetClaims() {
        event(a, "obtain", "#minecraft:logs", 16);
        Quest q = pack.quests().getFirst();
        engine.claim(a, q.id(), delivery(true));
        Objective changed =
                new Objective("main", "obtain", "minecraft:diamond", 16, "Diamonds", null, 0, 0, 0, 0, 0, 0);
        engine.reload(catalog(withQuests(List.of(copy(q, List.of(), List.of(changed), q.rewards(), false)))));
        assertFalse(done("first_steps"));
        assertTrue(engine.claimed(a, q.id()));
        event(a, "obtain", "minecraft:diamond", 16);
        assertTrue(done("first_steps"));
        assertThrows(IllegalArgumentException.class, () -> engine.claim(a, q.id(), delivery(true)));
    }

    @Test
    void oneEventCannotSpillIntoNewlyUnlockedQuest() {
        Quest first = pack.quests().getFirst();
        Quest next = pack.quests().get(1);
        next = copy(next, List.of(first.id()), first.objectives(), next.rewards(), false);
        engine = new QuestEngine(state, catalog(withQuests(List.of(first, next))));
        event(a, "obtain", "#minecraft:logs", 32);
        assertTrue(done("first_steps"));
        assertFalse(done("getting_equipped"));
        event(a, "obtain", "#minecraft:logs", 16);
        assertTrue(done("getting_equipped"));
    }

    @Test
    void retroactiveConfigurationAccumulatesButWaitsForPrerequisite() {
        Quest first = pack.quests().getFirst(), next = pack.quests().get(1);
        next = copy(next, next.prerequisites(), next.objectives(), next.rewards(), true);
        engine = new QuestEngine(state, catalog(withQuests(List.of(first, next))));
        event(a, "craft", "minecraft:wooden_pickaxe", 1);
        assertFalse(done("getting_equipped"));
        event(a, "obtain", "#minecraft:logs", 16);
        assertTrue(done("getting_equipped"));
    }

    @Test
    void ownerTransferAndSoloRestoreArePredictable() {
        event(a, "obtain", "#minecraft:logs", 8);
        team();
        engine.teams().leave(a);
        assertEquals(b, engine.teams().team(b).owner);
        assertTrue(engine.teams().team(a).solo);
        assertEquals(
                8,
                engine.count(
                        engine.teams().team(a),
                        pack.quests().getFirst(),
                        pack.quests().getFirst().objectives().getFirst()));
    }

    @Test
    void expiredInvitationsAreRejected() {
        engine.teams().create(a, "Test");
        engine.teams().invite(a, b, 100);
        assertThrows(IllegalArgumentException.class, () -> engine.teams().accept(b, 300_101));
    }

    @Test
    void targetedSubmissionDoesNotAdvanceAnotherQuest() {
        Quest q = pack.quests().getFirst(), next = pack.quests().get(1);
        Objective o = new Objective("main", "submit", "minecraft:stone", 16, "Submit", null, 0, 0, 0, 0, 0, 0);
        q = copy(q, List.of(), List.of(o), q.rewards(), false);
        next = copy(next, List.of(), List.of(o), next.rewards(), false);
        engine = new QuestEngine(state, catalog(withQuests(List.of(q, next))));
        engine.completeAction(a, q.id(), o.id(), 16);
        assertTrue(done("first_steps"));
        assertFalse(done("getting_equipped"));
    }

    @Test
    void dimensionRegionKillPossessAndManualObjectivesWork() {
        Objective dimension =
                new Objective("dim", "dimension", "minecraft:the_nether", 1, "Visit", null, 0, 0, 0, 0, 0, 0);
        Objective region =
                new Objective("region", "region", null, 1, "Enter", "minecraft:overworld", 0, 0, 0, 10, 100, 10);
        Objective kill = new Objective("kill", "kill", "minecraft:zombie", 2, "Kill", null, 0, 0, 0, 0, 0, 0);
        Objective possess = new Objective("have", "possess", "minecraft:diamond", 3, "Have", null, 0, 0, 0, 0, 0, 0);
        Objective manual = new Objective("check", "manual", null, 1, "Check", null, 0, 0, 0, 0, 0, 0);
        Quest q = copy(
                pack.quests().getFirst(),
                List.of(),
                List.of(dimension, region, kill, possess, manual),
                List.of(),
                false);
        engine = new QuestEngine(state, catalog(withQuests(List.of(q))));
        event(a, "dimension", "minecraft:the_nether", 1);
        engine.contribute(a, new Contribution("region", Set.of(), 1, "minecraft:overworld", 5, 60, 5));
        event(a, "kill", "minecraft:zombie", 2);
        event(a, "possess", "minecraft:diamond", 2);
        event(a, "possess", "minecraft:diamond", 2);
        assertEquals(2, engine.count(engine.teams().team(a), q, possess));
        event(a, "possess", "minecraft:diamond", 3);
        assertFalse(done("first_steps"));
        engine.completeAction(a, q.id(), manual.id(), 1);
        assertTrue(done("first_steps"));
    }

    @Test
    void possessionAggregatesSnapshotsAcrossTeamMembersWithoutCountingChecksTwice() {
        team();
        Quest q = pack.quests().getFirst();
        Objective o = new Objective("main", "possess", "minecraft:wheat", 20, "Have wheat", null, 0, 0, 0, 0, 0, 0);
        q = copy(q, List.of(), List.of(o), q.rewards(), false);
        engine = new QuestEngine(state, catalog(withQuests(List.of(q))));
        event(a, "possess", "minecraft:wheat", 8);
        event(a, "possess", "minecraft:wheat", 8);
        assertFalse(done("first_steps"));
        event(b, "possess", "minecraft:wheat", 12);
        assertTrue(done("first_steps"));
    }

    @Test
    void twentyWheatCanBeCollectedByTwoPlayers() {
        team();
        Quest q = pack.quests().getFirst();
        Objective o = new Objective("main", "obtain", "minecraft:wheat", 20, "Collect wheat", null, 0, 0, 0, 0, 0, 0);
        q = copy(q, List.of(), List.of(o), q.rewards(), false);
        engine = new QuestEngine(state, catalog(withQuests(List.of(q))));
        event(a, "obtain", "minecraft:wheat", 8);
        event(b, "obtain", "minecraft:wheat", 12);
        assertTrue(done("first_steps"));
    }

    @Test
    void zoomKeepsCursorWorldCoordinateStable() {
        CanvasTransform canvas = new CanvasTransform();
        canvas.panX = 20;
        canvas.panY = 40;
        double x = (120 - canvas.panX) / canvas.zoom, y = (90 - canvas.panY) / canvas.zoom;
        canvas.zoomAt(120, 90, 1.15);
        assertEquals(120, canvas.screenX(x), 1e-8);
        assertEquals(90, canvas.screenY(y), 1e-8);
    }
}
