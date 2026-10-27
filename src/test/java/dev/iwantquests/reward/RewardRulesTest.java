package dev.iwantquests.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.iwantquests.quest.QuestCatalog;
import dev.iwantquests.quest.QuestPack;
import dev.iwantquests.quest.QuestPack.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RewardRulesTest {
    private static final Gson JSON = new Gson();
    private static final String CHAPTER = "iwantquests:chapter";
    private static final Objective TASK = Objective.simple("main", "kill", "minecraft:zombie", 1, "");

    private static void target(String kind, String value) {
        if (value == null || !value.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid target " + value);
    }

    private static QuestCatalog catalog(Reward... rewards) {
        Quest quest = new Quest(
                "iwantquests:q",
                CHAPTER,
                "Title",
                "",
                "minecraft:stone",
                0,
                0,
                List.of(),
                List.of(TASK),
                List.of(rewards),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
        return new QuestCatalog(
                new QuestPack(
                        List.of(new Chapter(CHAPTER, "Chapter", "minecraft:stone", 0, null, null, null, null)),
                        List.of(quest)),
                RewardRulesTest::target);
    }

    private static String rejection(Reward... rewards) {
        return assertThrows(IllegalArgumentException.class, () -> catalog(rewards))
                .getMessage();
    }

    private static Reward item(String id, int count) {
        return new Reward("item", id, count);
    }

    private static Reward option(Reward reward, int weight) {
        return new Reward(reward.type(), reward.target(), reward.count(), null, null, weight, null);
    }

    private static Reward container(String type, int count, Reward... options) {
        return new Reward(type, null, count, null, null, null, List.of(options));
    }

    // ---------------------------------------------------------------- definitions

    @Test
    void everyBuiltInTypeIsAccepted() {
        assertDoesNotThrow(() -> catalog(
                item("minecraft:bread", 3),
                new Reward("xp", null, 10),
                new Reward("command", "give {player} minecraft:diamond", 1),
                new Reward("loot_table", "minecraft:chests/simple_dungeon", 2),
                container("random", 1, option(item("minecraft:bread", 1), 3), option(new Reward("xp", null, 5), 1)),
                container("choice", 0, item("minecraft:bread", 1), item("minecraft:apple", 2))));
    }

    @Test
    void commandRewardNeedsOneShortLine() {
        assertTrue(rejection(new Reward("command", " ", 1)).contains("needs a command"));
        assertTrue(rejection(new Reward("command", "say a\nsay b", 1)).contains("one line"));
        assertTrue(rejection(new Reward("command", "say " + "x".repeat(600), 1)).contains("one line"));
        assertTrue(rejection(new Reward("command", "say hi", 0)).contains("count"));
        assertTrue(rejection(new Reward("command", "say hi", 101)).contains("count"));
    }

    @Test
    void lootTableNeedsAnIdentifier() {
        assertTrue(rejection(new Reward("loot_table", "not an id", 1)).contains("loot table ID"));
        assertTrue(rejection(new Reward("loot_table", null, 1)).contains("loot table ID"));
    }

    @Test
    void itemAndXpStillNeedAPositiveCount() {
        assertTrue(rejection(item("minecraft:stone", 0)).contains("count"));
        assertTrue(rejection(new Reward("xp", null, 0)).contains("count"));
    }

    @Test
    void containersNeedOptionsAndCannotNest() {
        assertTrue(rejection(container("random", 1)).contains("options"));
        assertTrue(rejection(container("choice", 1)).contains("options"));
        assertTrue(rejection(container("choice", 1, container("random", 1, item("minecraft:bread", 1))))
                .contains("cannot contain"));
        assertTrue(rejection(container("random", 1, item("minecraft:bread", 0))).contains("count"));
        assertTrue(rejection(container("choice", 2, item("minecraft:bread", 1))).contains("count must be 1"));
    }

    @Test
    void onlyContainersMayHaveOptions() {
        Reward bad = new Reward("item", "minecraft:bread", 1, null, null, null, List.of(item("minecraft:stone", 1)));
        assertTrue(rejection(bad).contains("cannot have options"));
    }

    @Test
    void weightsAndDisplayFieldsAreChecked() {
        assertTrue(rejection(container("random", 1, option(item("minecraft:bread", 1), 0)))
                .contains("weight"));
        assertTrue(rejection(container("random", 1, option(item("minecraft:bread", 1), 5000)))
                .contains("weight"));
        assertTrue(rejection(new Reward("command", "say hi", 1, "n".repeat(101), null, null, null))
                .contains("name"));
        assertTrue(rejection(new Reward("command", "say hi", 1, null, "bad icon", null, null))
                .contains("Invalid target"));
    }

    @Test
    void unknownRewardTypeIsRejected() {
        assertTrue(rejection(new Reward("teleport", null, 1)).contains("Unknown reward type"));
    }

    @Test
    void rewardsKeepTheirOptionsThroughJson() {
        Reward choice = new Reward(
                "choice",
                null,
                1,
                "Pick one",
                "minecraft:chest",
                null,
                List.of(option(item("minecraft:bread", 2), 4), new Reward("xp", null, 9)));
        Reward back = JSON.fromJson(JSON.toJson(choice), Reward.class);
        assertEquals(choice, back);
        assertEquals(4, back.optionsOrEmpty().getFirst().weightOrDefault());
        assertEquals(1, back.optionsOrEmpty().get(1).weightOrDefault());
        assertFalse(
                JSON.toJson(item("minecraft:bread", 1)).contains("options"), "plain rewards stay as small as before");
    }

    @Test
    void oldRewardJsonStillReads() {
        Reward reward = JSON.fromJson("{\"type\":\"item\",\"target\":\"minecraft:bread\",\"count\":8}", Reward.class);
        assertEquals(item("minecraft:bread", 8), reward);
        assertEquals(List.of(), reward.optionsOrEmpty());
    }

    @Test
    void playersNeverReceiveCommandText() {
        Reward command = new Reward("command", "op {player}", 1, "A secret", "minecraft:nether_star", null, null);
        Reward nested = container("choice", 0, item("minecraft:bread", 1), new Reward("command", "say hidden", 1));
        Quest quest = new Quest(
                "iwantquests:q",
                CHAPTER,
                "Title",
                "",
                "minecraft:stone",
                0,
                0,
                List.of(),
                List.of(TASK),
                List.of(command, nested, item("minecraft:apple", 1)),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
        QuestPack pack = new QuestPack(List.of(), List.of(quest));
        String sent = JSON.toJson(pack.withoutCommands());
        assertFalse(sent.contains("op {player}") || sent.contains("say hidden"));
        QuestPack shown = pack.withoutCommands();
        Reward shownCommand = shown.quests().getFirst().rewards().getFirst();
        assertEquals("command", shownCommand.type());
        assertEquals("A secret", shownCommand.name());
        assertEquals("minecraft:nether_star", shownCommand.icon());
        assertEquals(
                2, shown.quests().getFirst().rewards().get(1).optionsOrEmpty().size());
        assertEquals(
                item("minecraft:apple", 1), shown.quests().getFirst().rewards().get(2));
        assertTrue(
                pack.quests().getFirst().rewards().getFirst().target().equals("op {player}"),
                "the server keeps the real command");
    }

    @Test
    void packsWithoutCommandsAreSentAsTheyAre() {
        QuestCatalog catalog = catalog(item("minecraft:bread", 1));
        assertSame(catalog.pack(), catalog.pack().withoutCommands());
    }

    // ---------------------------------------------------------------- resolving a claim

    @Test
    void plainRewardsPassThroughUnchanged() {
        List<Reward> rewards = List.of(item("minecraft:bread", 1), new Reward("xp", null, 5));
        assertEquals(rewards, RewardResolver.resolve(rewards, bound -> 0, List.of()));
    }

    @Test
    void randomRewardPicksByWeight() {
        Reward common = option(item("minecraft:bread", 1), 3), rare = option(item("minecraft:diamond", 1), 1);
        List<Reward> rewards = List.of(container("random", 1, common, rare));
        for (int roll = 0; roll < 3; roll++) {
            int fixed = roll;
            assertEquals(
                    common,
                    RewardResolver.resolve(
                                    rewards,
                                    bound -> {
                                        assertEquals(4, bound);
                                        return fixed;
                                    },
                                    List.of())
                            .getFirst());
        }
        assertEquals(
                rare, RewardResolver.resolve(rewards, bound -> 3, List.of()).getFirst());
    }

    @Test
    void randomCountRollsSeveralTimes() {
        Reward a = item("minecraft:bread", 1), b = item("minecraft:apple", 1);
        Iterator<Integer> rolls = List.of(0, 1, 0).iterator();
        List<Reward> result =
                RewardResolver.resolve(List.of(container("random", 3, a, b)), bound -> rolls.next(), List.of());
        assertEquals(List.of(a, b, a), result);
    }

    @Test
    void randomRewardsFollowTheRealDistribution() {
        Reward common = option(item("minecraft:bread", 1), 9), rare = option(item("minecraft:diamond", 1), 1);
        Random random = new Random(7);
        int rareCount = 0, trials = 20_000;
        for (int i = 0; i < trials; i++)
            if (RewardResolver.resolve(List.of(container("random", 1, common, rare)), random::nextInt, List.of())
                            .getFirst()
                    == rare) rareCount++;
        assertEquals(0.1, rareCount / (double) trials, 0.015);
    }

    @Test
    void choiceRewardUsesThePickedOption() {
        Reward bread = item("minecraft:bread", 1), apple = item("minecraft:apple", 1);
        List<Reward> rewards = List.of(new Reward("xp", null, 5), container("choice", 1, bread, apple));
        assertEquals(List.of(rewards.getFirst(), apple), RewardResolver.resolve(rewards, bound -> 0, List.of(1)));
    }

    @Test
    void severalChoicesAreAnsweredInOrder() {
        Reward a = item("minecraft:bread", 1), b = item("minecraft:apple", 1), c = item("minecraft:carrot", 1);
        List<Reward> rewards = List.of(container("choice", 1, a, b), container("choice", 1, b, c));
        assertEquals(List.of(b, c), RewardResolver.resolve(rewards, bound -> 0, List.of(1, 1)));
        assertEquals(2, RewardResolver.choiceCount(rewards));
    }

    @Test
    void missingExtraOrInvalidChoicesAreRejected() {
        List<Reward> rewards = List.of(container("choice", 1, item("minecraft:bread", 1), item("minecraft:apple", 1)));
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> RewardResolver.resolve(rewards, bound -> 0, List.of()))
                .getMessage()
                .contains("Choose"));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.resolve(rewards, bound -> 0, List.of(2)));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.resolve(rewards, bound -> 0, List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.resolve(rewards, bound -> 0, List.of(0, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> RewardResolver.resolve(List.of(item("minecraft:bread", 1)), bound -> 0, List.of(0)));
    }

    @Test
    void choiceTextParsing() {
        assertEquals(List.of(), RewardResolver.parseChoices(""));
        assertEquals(List.of(), RewardResolver.parseChoices(null));
        assertEquals(List.of(1, 0, 3), RewardResolver.parseChoices("1, 0,3"));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.parseChoices("a"));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.parseChoices("1,,2"));
        assertThrows(IllegalArgumentException.class, () -> RewardResolver.parseChoices("0,".repeat(40) + "0"));
    }
}
