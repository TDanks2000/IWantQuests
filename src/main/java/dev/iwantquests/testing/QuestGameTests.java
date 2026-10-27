package dev.iwantquests.testing;

import dev.iwantquests.QuestConfig;
import dev.iwantquests.engine.*;
import dev.iwantquests.network.EditPayload;
import dev.iwantquests.network.EditorInfo;
import dev.iwantquests.progress.*;
import dev.iwantquests.quest.*;
import dev.iwantquests.reward.InventoryRewards;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Runs only when the official GameTest harness is enabled. */
public final class QuestGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "iwantquests");
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            LOOP = FUNCTIONS.register("playable_loop", () -> QuestGameTests::playableLoop);
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            STORAGE = FUNCTIONS.register("disk_persistence", () -> QuestGameTests::diskPersistence);
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            VALIDATION = FUNCTIONS.register("registry_validation", () -> QuestGameTests::registryValidation);
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            CONFIG = FUNCTIONS.register("config_quests", () -> QuestGameTests::configQuests);
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            ACCESS = FUNCTIONS.register("editor_access", () -> QuestGameTests::editorAccess);
    private static final net.neoforged.neoforge.registries.DeferredHolder<
                    Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            REWARDS = FUNCTIONS.register("reward_types", () -> QuestGameTests::rewardTypes);

    public static void register(IEventBus bus) {
        FUNCTIONS.register(bus);
        bus.addListener((RegisterGameTestsEvent e) -> {
            Holder<TestEnvironmentDefinition<?>> env =
                    e.registerEnvironment(id("environment"), new TestEnvironmentDefinition.AllOf(List.of()));
            for (var function : List.of(LOOP, STORAGE, VALIDATION, CONFIG, ACCESS, REWARDS)) {
                e.registerTest(
                        function.getId(),
                        new FunctionGameTestInstance(
                                function.getKey(), new TestData<>(env, id("empty"), 200, 0, true)));
            }
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("iwantquests", path);
    }

    @SuppressWarnings("removal")
    private static void editorAccess(GameTestHelper h) {
        boolean enabled = QuestConfig.EDITOR_ENABLED.get();
        var authors = QuestConfig.EDITOR_PLAYERS.get();
        var server = h.getLevel().getServer();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var service = ServerQuests.get(server);
        var before = service.engine().catalog();
        try {
            QuestConfig.EDITOR_ENABLED.set(false);
            QuestConfig.EDITOR_PLAYERS.set(List.of(player.getUUID().toString()));
            h.assertTrue(!ServerQuests.canEdit(player), "Disabled editor rejects even an allowlisted author");
            QuestConfig.EDITOR_ENABLED.set(true);
            h.assertTrue(ServerQuests.canEdit(player), "Trusted author can edit without operator access");
            QuestConfig.EDITOR_PLAYERS.set(List.of());
            h.assertTrue(!ServerQuests.canEdit(player), "Ordinary player has no editor access");
            h.assertTrue(
                    service.editorInfo(player).equals(EditorInfo.NONE),
                    "No editable metadata is sent to ordinary players");
            for (String op : List.of(
                    "create_chapter",
                    "create_quest",
                    "patch",
                    "delete",
                    "undo",
                    "redo",
                    "paste",
                    "save_template",
                    "delete_template",
                    "publish",
                    "discard",
                    "draft_open")) service.edit(player, new EditPayload(op, "{}"));
            h.assertTrue(
                    service.engine().catalog() == before, "Unauthorized edit payloads leave the catalogue untouched");
            server.getPlayerList()
                    .op(
                            new NameAndId(player.getGameProfile()),
                            Optional.of(LevelBasedPermissionSet.MODERATOR),
                            Optional.empty());
            h.assertTrue(!ServerQuests.canEdit(player), "Level 1 operator cannot author quests");
            server.getPlayerList()
                    .op(
                            new NameAndId(player.getGameProfile()),
                            Optional.of(LevelBasedPermissionSet.GAMEMASTER),
                            Optional.empty());
            h.assertTrue(ServerQuests.canEdit(player), "Operator can author when enabled");
            QuestConfig.EDITOR_ENABLED.set(false);
            h.assertTrue(!ServerQuests.canEdit(player), "Operator cannot bypass disabled authoring");
            h.succeed();
        } finally {
            server.getPlayerList().deop(new NameAndId(player.getGameProfile()));
            server.getPlayerList().remove(player);
            service.logout(player.getUUID());
            QuestConfig.EDITOR_ENABLED.set(enabled);
            QuestConfig.EDITOR_PLAYERS.set(authors);
        }
    }

    private static void configQuests(GameTestHelper h) {
        java.nio.file.Path directory = null;
        try {
            directory = Files.createTempDirectory("iwantquests-config-test-");
            var resources = h.getLevel().getServer().getResourceManager();
            h.assertTrue(
                    !QuestLoader.load(resources, directory, true).quests().isEmpty(), "Defaults load when enabled");
            h.assertTrue(
                    QuestLoader.load(resources, directory, false).quests().isEmpty(),
                    "Disabled defaults produce an empty book");
            Files.writeString(directory.resolve("custom.json"), """
                {"chapters":[{"id":"creator:start","title":"Custom chapter","icon":"minecraft:book"}],
                 "quests":[{"id":"creator:hello","chapter":"creator:start","title":"Welcome","icon":"minecraft:book",
                 "prerequisites":[],"objectives":[{"id":"read","type":"manual","count":1}],"rewards":[]}]}
                """);
            var custom = QuestLoader.load(resources, directory, false);
            h.assertTrue(
                    custom.quests().size() == 1 && custom.quest("creator:hello") != null,
                    "Custom config replaces the sample catalogue");
            Files.writeString(directory.resolve("custom.json"), "{\"chapters\":[],\"quests\":[]}");
            h.assertTrue(
                    QuestLoader.load(resources, directory, false).quests().isEmpty(),
                    "Edited config is re-read on the next load");
            h.succeed();
        } catch (Exception error) {
            throw new IllegalStateException("Config quest loading failed", error);
        } finally {
            if (directory != null) {
                try {
                    Files.deleteIfExists(directory.resolve("custom.json"));
                    Files.deleteIfExists(directory);
                } catch (java.io.IOException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
    }

    @SuppressWarnings("removal")
    private static void playableLoop(GameTestHelper h) {
        ServerPlayer a = h.makeMockServerPlayerInLevel(), b = h.makeMockServerPlayerInLevel();
        var server = h.getLevel().getServer();
        var service = ServerQuests.get(server);
        var engine = service.engine();
        engine.teams().create(a.getUUID(), "GameTest explorers");
        engine.teams().invite(a.getUUID(), b.getUUID(), 100);
        engine.teams().accept(b.getUUID(), 101);
        pickup(h, a, new ItemStack(Items.OAK_LOG, 8));
        h.assertTrue(
                !engine.completed(engine.teams().team(a.getUUID()), "iwantquests:first_steps"),
                "8 logs must not complete quest");
        pickup(h, b, new ItemStack(Items.BIRCH_LOG, 8));
        h.assertTrue(
                engine.completed(engine.teams().team(a.getUUID()), "iwantquests:first_steps"),
                "Tag pickups from two players must complete logs");
        craft(
                h,
                a,
                new ItemStack[] {
                    new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY,
                    new ItemStack(Items.STICK),
                    ItemStack.EMPTY,
                    ItemStack.EMPTY,
                    new ItemStack(Items.STICK),
                    ItemStack.EMPTY
                },
                Items.WOODEN_PICKAXE);
        h.assertTrue(
                engine.completed(engine.teams().team(b.getUUID()), "iwantquests:getting_equipped"),
                "Actual recipe take must count crafting");
        pickup(h, a, new ItemStack(Items.COBBLESTONE, 20));
        pickup(h, b, new ItemStack(Items.COBBLESTONE, 12));
        craft(
                h,
                b,
                new ItemStack[] {
                    new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_wool"))),
                    new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_wool"))),
                    new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_wool"))),
                    new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY,
                    ItemStack.EMPTY,
                    ItemStack.EMPTY
                },
                BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:red_bed")));
        craft(
                h,
                a,
                new ItemStack[] {
                    new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.IRON_INGOT),
                    ItemStack.EMPTY,
                    new ItemStack(Items.STICK),
                    ItemStack.EMPTY,
                    ItemStack.EMPTY,
                    new ItemStack(Items.STICK),
                    ItemStack.EMPTY
                },
                Items.IRON_PICKAXE);
        for (var q : engine.catalog().quests()) {
            h.assertTrue(
                    engine.completed(engine.teams().team(a.getUUID()), q.id()), "Quest did not complete: " + q.id());
            engine.claim(a.getUUID(), q.id(), new InventoryRewards(a));
            engine.claim(b.getUUID(), q.id(), new InventoryRewards(b));
        }
        h.assertTrue(a.totalExperience == 250 && b.totalExperience == 250, "Each player receives their own XP");
        int bread = count(a, Items.BREAD);
        h.assertTrue(bread == 8 && count(b, Items.BREAD) == 8, "Each player receives bread");
        boolean rejected = false;
        try {
            engine.claim(a.getUUID(), "iwantquests:first_steps", new InventoryRewards(a));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected && count(a, Items.BREAD) == bread, "Repeated claim must not grant bread");
        var viewA = service.snapshot(a, null);
        var viewB = service.snapshot(b, null);
        h.assertTrue(
                viewA.team().equals(viewB.team())
                        && viewA.quests().values().stream().allMatch(v -> v.completed() && v.claimed()),
                "Authoritative snapshots reflect shared completion and personal claims");
        // Reject a reward that cannot fit, without silently dropping or deleting items.
        for (int i = 0; i < a.getInventory().getNonEquipmentItems().size(); i++)
            a.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        h.assertTrue(
                !new InventoryRewards(a).canDeliver(List.of(new QuestPack.Reward("item", "minecraft:bread", 8))),
                "Full inventory must reject item reward");
        server.getPlayerList().remove(a);
        server.getPlayerList().remove(b);
        service.dirty();
        h.succeed();
    }

    @SuppressWarnings("removal")
    private static void rewardTypes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        var bread = new QuestPack.Reward("item", "minecraft:bread", 3);
        var apple = new QuestPack.Reward("item", "minecraft:apple", 2);
        var choice = new QuestPack.Reward("choice", null, 1, null, null, null, List.of(bread, apple));
        var picked = new InventoryRewards(p, List.of(1));
        h.assertTrue(picked.canDeliver(List.of(choice)), "A picked option fits");
        picked.deliver(List.of(choice));
        h.assertTrue(count(p, Items.APPLE) == 2 && count(p, Items.BREAD) == 0, "Only the chosen option is delivered");
        boolean rejected = false;
        try {
            new InventoryRewards(p).canDeliver(List.of(choice));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "A choice reward needs a pick");
        var random = new QuestPack.Reward(
                "random", null, 2, null, null, null, List.of(new QuestPack.Reward("item", "minecraft:carrot", 3)));
        var rolled = new InventoryRewards(p);
        h.assertTrue(rolled.canDeliver(List.of(random)), "A random reward fits");
        rolled.deliver(List.of(random));
        h.assertTrue(count(p, Items.CARROT) == 6, "Random reward is rolled once per count");
        var command = new QuestPack.Reward("command", "give @s minecraft:diamond 2", 1);
        var ran = new InventoryRewards(p);
        h.assertTrue(ran.canDeliver(List.of(command)), "A command reward needs no inventory room");
        ran.deliver(List.of(command));
        h.assertTrue(count(p, Items.DIAMOND) == 2, "Command reward runs as the claiming player");
        int before = p.getInventory().getNonEquipmentItems().stream()
                .mapToInt(ItemStack::getCount)
                .sum();
        var loot = new QuestPack.Reward("loot_table", "minecraft:chests/spawn_bonus_chest", 1);
        var looted = new InventoryRewards(p);
        h.assertTrue(looted.canDeliver(List.of(loot)), "Loot fits");
        looted.deliver(List.of(loot));
        h.assertTrue(
                p.getInventory().getNonEquipmentItems().stream()
                                .mapToInt(ItemStack::getCount)
                                .sum()
                        > before,
                "Loot table reward delivers items");
        rejected = false;
        try {
            new InventoryRewards(p)
                    .canDeliver(List.of(new QuestPack.Reward("loot_table", "iwantquests:no_such_table", 1)));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "A missing loot table is reported, not silently skipped");
        for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++)
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        h.assertTrue(!new InventoryRewards(p).canDeliver(List.of(loot)), "Loot is refused when the inventory is full");
        // Command rewards run with full permission, so the editor only accepts new ones from owners.
        var service = ServerQuests.get(server);
        var live = service.engine().catalog().pack();
        var base = live.quests().getFirst();
        var risky = new QuestPack.Quest(
                "iwantquests:guard_test",
                base.chapter(),
                "Guard",
                "",
                base.icon(),
                0,
                0,
                List.of(),
                base.objectives(),
                List.of(new QuestPack.Reward("command", "say hi", 1)),
                false,
                false,
                false,
                false,
                null,
                null,
                0,
                null);
        var quests = new ArrayList<>(live.quests());
        quests.add(risky);
        var changed = new QuestPack(live.chapters(), quests);
        service.authored(p.getUUID(), live);
        rejected = false;
        try {
            service.authored(p.getUUID(), changed);
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage().contains("command");
        }
        h.assertTrue(rejected, "A player without operator rights cannot add a command reward");
        server.getPlayerList()
                .op(
                        new NameAndId(p.getGameProfile()),
                        Optional.of(LevelBasedPermissionSet.GAMEMASTER),
                        Optional.empty());
        rejected = false;
        try {
            service.authored(p.getUUID(), changed);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "A level 2 operator cannot add a command reward");
        server.getPlayerList()
                .op(new NameAndId(p.getGameProfile()), Optional.of(LevelBasedPermissionSet.OWNER), Optional.empty());
        service.authored(p.getUUID(), changed);
        server.getPlayerList().deop(new NameAndId(p.getGameProfile()));
        server.getPlayerList().remove(p);
        h.succeed();
    }

    private static void pickup(GameTestHelper h, ServerPlayer p, ItemStack stack) {
        var item = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), stack);
        item.setNoPickUpDelay();
        h.getLevel().addFreshEntity(item);
        item.playerTouch(p);
        h.assertTrue(item.isRemoved(), "Test pickup must enter inventory");
    }

    private static void craft(GameTestHelper h, ServerPlayer p, ItemStack[] inputs, Item result) {
        var menu = new CraftingMenu(
                1,
                p.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))));
        p.containerMenu = menu;
        for (int i = 0; i < 9; i++) menu.getSlot(i + 1).set(inputs[i]);
        h.assertTrue(menu.getSlot(0).getItem().is(result), "Recipe must produce " + result);
        ItemStack crafted = menu.getSlot(0).safeTake(1, 64, p);
        p.getInventory().add(crafted);
        p.containerMenu = p.inventoryMenu;
    }

    private static int count(ServerPlayer player, Item item) {
        int result = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems())
            if (stack.is(item)) result += stack.getCount();
        return result;
    }

    private static void diskPersistence(GameTestHelper h) {
        try {
            var server = h.getLevel().getServer();
            var pack = server.getServerResources()
                    .managers()
                    .getListener(QuestLoader.KEY)
                    .catalog();
            var data = new QuestSavedData();
            var engine = new QuestEngine(data.state(), pack);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            engine.teams().create(a, "Disk test");
            engine.teams().invite(a, b, 100);
            engine.teams().accept(b, 101);
            engine.contribute(
                    a,
                    dev.iwantquests.objective.ObjectiveRegistry.Contribution.item(
                            "obtain", Set.of("#minecraft:logs"), 16));
            engine.claim(a, "iwantquests:first_steps", new QuestEngine.RewardDelivery() {
                public boolean canDeliver(List<QuestPack.Reward> rewards) {
                    return true;
                }

                public void deliver(List<QuestPack.Reward> rewards) {}
            });
            var folder = Files.createTempDirectory("iwantquests-gametest-");
            try (var storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), server.registryAccess())) {
                storage.set(QuestSavedData.TYPE, data);
                data.setDirty();
                storage.saveAndJoin();
            }
            try (var storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), server.registryAccess())) {
                var restored = storage.computeIfAbsent(QuestSavedData.TYPE);
                var reloaded = new QuestEngine(restored.state(), pack);
                h.assertTrue(
                        reloaded.teams().team(a).id.equals(reloaded.teams().team(b).id),
                        "Team membership must survive fresh storage instance");
                h.assertTrue(
                        reloaded.completed(reloaded.teams().team(b), "iwantquests:first_steps"),
                        "Completed quest must survive disk reload");
                h.assertTrue(
                        reloaded.claimed(a, "iwantquests:first_steps")
                                && !reloaded.claimed(b, "iwantquests:first_steps"),
                        "Individual claims must survive disk reload");
            }
            h.succeed();
        } catch (Exception error) {
            throw new RuntimeException("Disk persistence test failed", error);
        }
    }

    private static void registryValidation(GameTestHelper h) {
        boolean itemRejected = false, entityRejected = false;
        try {
            QuestLoader.validateTarget("item", "minecraft:iwantquests_missing_item");
        } catch (IllegalArgumentException error) {
            itemRejected = error.getMessage().contains("Unknown item");
        }
        try {
            QuestLoader.validateTarget("entity", "minecraft:iwantquests_missing_entity");
        } catch (IllegalArgumentException error) {
            entityRejected = error.getMessage().contains("Unknown entity");
        }
        h.assertTrue(itemRejected && entityRejected, "Invalid registered identifiers must produce useful errors");
        QuestLoader.validateTarget("item", "minecraft:iron_pickaxe");
        QuestLoader.validateTarget("entity", "minecraft:zombie");
        h.succeed();
    }

    private QuestGameTests() {}
}
