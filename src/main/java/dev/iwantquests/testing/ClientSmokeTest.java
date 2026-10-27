package dev.iwantquests.testing;

import dev.iwantquests.IWantQuests;
import dev.iwantquests.client.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in development-only smoke run. Creates its own world and uses real gameplay pickup events. */
public final class ClientSmokeTest {
    private static int stage, ticks, choiceStep, choiceMark, choiceDone;
    private static long choiceSent;
    private static boolean restarting;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientSmokeTest::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        ticks++;
        if (stage >= 2)
            mc.gui.toastManager().clear(); // the pickup's recipe toast would cover the book's top-right controls
        try {
            if (stage == 0 && mc.gui.screen() != null) {
                stage = 1;
                ticks = 0;
                mc.options.renderDistance().set(4);
                mc.options.guiScale().set(2);
                mc.resizeGui();
                Path marker = mc.gameDirectory.toPath().resolve("iwantquests-smoke-world.txt");
                restarting = Files.exists(marker);
                if (restarting) mc.createWorldOpenFlows().openWorld(Files.readString(marker), mc::stop);
                else {
                    String world = "iwantquests-smoke-" + System.currentTimeMillis();
                    Files.writeString(marker, world);
                    mc.createWorldOpenFlows()
                            .createFreshLevel(
                                    world,
                                    new LevelSettings(
                                            "IWantQuests smoke test",
                                            GameType.SURVIVAL,
                                            LevelSettings.DifficultySettings.DEFAULT,
                                            true,
                                            WorldDataConfiguration.DEFAULT),
                                    new WorldOptions(42, false, false),
                                    WorldPresets::createNormalWorldDimensions,
                                    mc.gui.screen());
                }
            }
            if (stage == 1 && mc.player != null && ClientQuestState.snapshot != null) {
                if (restarting) {
                    var first = ClientQuestState.snapshot.quests().get("iwantquests:first_steps");
                    if (!first.completed() || !first.claimed())
                        throw new IllegalStateException("Progress or claim lost after actual client/server restart");
                    IWantQuests.LOGGER.info("IWANTQUESTS_SMOKE: restart restored completed quest and personal claim");
                } else {
                    var uuid = mc.player.getUUID();
                    mc.getSingleplayerServer().execute(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                        var item = new ItemEntity(
                                p.level(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.OAK_LOG, 16));
                        item.setNoPickUpDelay();
                        p.level().addFreshEntity(item);
                        item.playerTouch(p);
                    });
                }
                stage = 2;
                ticks = 0;
            }
            if (stage == 2 && ticks == 30) capture(mc, "quest-world.png");
            if (stage == 2
                    && ticks == 32
                    && Files.exists(mc.gameDirectory.toPath().resolve("config/iwantquests/quests/bulk.json"))) {
                // build.gradle generates 2,500 quests (over 1 MB), which has to arrive in several network parts.
                long bulk = ClientQuestState.pack.quests().stream()
                        .filter(q -> q.id().startsWith("bulk:"))
                        .count();
                int characters = dev.iwantquests.quest.QuestLoader.JSON
                        .toJson(ClientQuestState.pack)
                        .length();
                if (bulk != 2500 || characters < 1_000_000)
                    throw new IllegalStateException("Large catalogue did not arrive intact: " + bulk + " quests, "
                            + characters + " characters");
                IWantQuests.LOGGER.info(
                        "IWANTQUESTS_SMOKE: large catalogue of {} quests ({} characters) arrived intact in parts",
                        bulk,
                        characters);
            }
            if (stage == 2 && ticks > 40) {
                var first = ClientQuestState.snapshot.quests().get("iwantquests:first_steps");
                if (!first.completed()
                        || !ClientQuestState.snapshot
                                .quests()
                                .get("iwantquests:getting_equipped")
                                .available()) throw new IllegalStateException("Real server delta did not reach client");
                mc.gui.setScreen(new QuestBookScreen());
                stage = 3;
                ticks = 0;
            }
            if (stage == 3 && ticks == 30) capture(mc, "quest-tree.png");
            if (stage == 3 && ticks == 35) ((QuestBookScreen) mc.gui.screen()).toggleChapters();
            if (stage == 3 && ticks == 45) capture(mc, "quest-tree-expanded.png");
            if (stage == 3 && ticks == 47) ((QuestBookScreen) mc.gui.screen()).toggleChapters();
            if (stage == 3 && ticks == 55) capture(mc, "quest-tree-collapsed.png");
            if (stage == 3 && ticks == 60) {
                ((QuestBookScreen) mc.gui.screen()).focusQuest("iwantquests:first_steps");
            }
            if (stage == 3 && ticks == 80) capture(mc, "quest-details.png");
            if (stage == 3 && ticks == 85) {
                ((QuestBookScreen) mc.gui.screen()).focusQuest("iwantquests:a_safe_place");
            }
            if (stage == 3 && ticks == 92) capture(mc, "quest-details-locked.png");
            if (stage == 3 && ticks == 96) {
                ((QuestBookScreen) mc.gui.screen()).focusQuest("iwantquests:first_steps");
            }
            if (stage == 3 && ticks == 100 && !restarting)
                ClientQuestState.action("claim", "iwantquests:first_steps", "");
            if (stage == 3 && ticks == 110) ClientQuestState.action("claim", "iwantquests:first_steps", "");
            if (stage == 3 && ticks == 140) {
                if (!ClientQuestState.snapshot
                        .quests()
                        .get("iwantquests:first_steps")
                        .claimed()) throw new IllegalStateException("Server reward claim did not sync");
                int bread = mc.player.getInventory().getNonEquipmentItems().stream()
                        .filter(s -> s.is(Items.BREAD))
                        .mapToInt(ItemStack::getCount)
                        .sum();
                if (bread != 8)
                    throw new IllegalStateException("Duplicate network claim changed bread quantity: " + bread);
                if (!ClientQuestState.feedback.contains("already claimed"))
                    throw new IllegalStateException("Duplicate claim request was not rejected");
                IWantQuests.LOGGER.info(
                        "IWANTQUESTS_SMOKE: duplicate network claim rejected; inventory still contains exactly eight bread");
                capture(mc, "quest-claimed.png");
                mc.options.guiScale().set(4);
                mc.resizeGui();
            }
            if (stage == 3 && ticks == 150) mc.getWindow().setWindowed(640, 480);
            if (stage == 3 && ticks == 165) capture(mc, "quest-narrow.png");
            if (stage == 3 && ticks == 170) ((QuestBookScreen) mc.gui.screen()).mouseScrolled(160, 120, 0, -20);
            if (stage == 3 && ticks == 180) capture(mc, "quest-narrow-scrolled.png");
            if (stage == 3 && ticks == 185) {
                mc.getWindow().setWindowed(854, 480);
                mc.options.guiScale().set(2);
                mc.resizeGui();
                mc.gui.setScreen(new TeamScreen(null));
            }
            if (stage == 3 && ticks == 205) capture(mc, "team-solo.png");
            if (stage == 3 && ticks == 210) ClientQuestState.action("team_create", "Explorers", "");
            if (stage == 3 && ticks == 240) {
                if (ClientQuestState.team == null || ClientQuestState.team.solo())
                    throw new IllegalStateException("Team screen did not create a team");
                IWantQuests.LOGGER.info("IWANTQUESTS_SMOKE: team created from the Team screen action");
                capture(mc, "team.png");
            }
            if (stage == 3 && ticks == 245) {
                ClientConfig.overrideTheme("iwantquests:classic");
                mc.gui.setScreen(new QuestBookScreen());
            }
            if (stage == 3 && ticks == 256) capture(mc, "theme-classic.png");
            if (stage == 3 && ticks == 258) {
                ClientConfig.overrideTheme("iwantquests:midnight");
                mc.gui.setScreen(new QuestBookScreen());
            }
            if (stage == 3 && ticks == 269) capture(mc, "theme-midnight.png");
            if (stage == 3 && ticks == 271) ClientConfig.overrideTheme("");
            // Reward showcase (examples/rewards, copied into the smoke config): choice, random, loot and repeatable
            // rewards.
            if (stage == 3 && ticks == 273) mc.gui.setScreen(new QuestBookScreen());
            if (stage == 3 && ticks == 276) ((QuestBookScreen) mc.gui.screen()).jumpToQuest("examples:pick_a_prize");
            if (stage == 3 && ticks == 290) capture(mc, "reward-choice-locked.png");
            if (stage == 3 && ticks == 292) ClientQuestState.action("manual", "examples:pick_a_prize", "done");
            if (stage == 3 && ticks == 305) capture(mc, "reward-choice.png");
            // The server drops requests under 150 ms apart and client ticks can run in bursts, so wait for replies and
            // real time.
            if (stage == 3 && ticks == 306) {
                if (restarting) {
                    choiceStep = 3;
                    choiceDone = ticks;
                } // the first run already claimed it; only the screenshots repeat
                else {
                    choiceMark = ClientQuestState.feedbackSerial;
                    choiceSent = System.currentTimeMillis();
                    ClientQuestState.action("claim", "examples:pick_a_prize", "");
                }
            }
            if (stage == 3 && ticks > 306 && choiceStep == 0 && ClientQuestState.feedbackSerial > choiceMark) {
                if (!ClientQuestState.feedback.contains("Choose a reward"))
                    throw new IllegalStateException(
                            "A choice reward was claimed without a pick: " + ClientQuestState.feedback);
                choiceStep = 1;
            }
            if (stage == 3 && choiceStep == 1 && System.currentTimeMillis() - choiceSent >= 600) {
                ClientQuestState.action("claim", "examples:pick_a_prize", "1");
                choiceStep = 2;
                choiceDone = ticks;
            }
            if (stage == 3
                    && choiceStep == 2
                    && ClientQuestState.snapshot
                            .quests()
                            .get("examples:pick_a_prize")
                            .claimed()) {
                int apples = mc.player.getInventory().getNonEquipmentItems().stream()
                        .filter(s -> s.is(Items.APPLE))
                        .mapToInt(ItemStack::getCount)
                        .sum();
                int xp = mc.player.totalExperience;
                if (apples != 4 || xp >= 200)
                    throw new IllegalStateException(
                            "Choice reward delivered the wrong option: apples=" + apples + " xp=" + xp);
                IWantQuests.LOGGER.info(
                        "IWANTQUESTS_SMOKE: choice reward delivered only the picked option over the network");
                capture(mc, "reward-choice-claimed.png");
                ((QuestBookScreen) mc.gui.screen()).jumpToQuest("examples:lucky_dip");
                choiceStep = 3;
                choiceDone = ticks;
            }
            if (stage == 3 && choiceStep == 2 && ticks > choiceDone + 120)
                throw new IllegalStateException(
                        "Choice reward claim did not sync; server said: " + ClientQuestState.feedback);
            if (stage == 3 && choiceStep == 3 && ticks == choiceDone + 12) {
                capture(mc, "reward-random.png");
                ((QuestBookScreen) mc.gui.screen()).jumpToQuest("examples:zombie_farm");
            }
            if (stage == 3 && choiceStep == 3 && ticks == choiceDone + 25) {
                capture(mc, "reward-repeatable.png");
                choiceStep = 4;
                choiceDone = ticks;
            }
            if (stage == 3 && choiceStep == 4 && ticks > choiceDone + 10) {
                IWantQuests.LOGGER.info(
                        "IWANTQUESTS_SMOKE: real initial sync, pickup, unlock, reward claim and GUI render passed");
                stage = 4;
                ticks = 0;
            }
            if (stage == 4 && EditorSmokeTest.tick(mc)) {
                stage = 5;
                mc.stop();
            }
            if (ticks > 1800) throw new IllegalStateException("Client smoke test timed out at stage " + stage);
        } catch (Exception error) {
            IWantQuests.LOGGER.error("IWANTQUESTS_SMOKE_FAILED", error);
            stage = 5;
            mc.stop();
        }
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(
                mc.gameDirectory,
                name,
                mc.gameRenderer.mainRenderTarget(),
                1,
                message -> IWantQuests.LOGGER.info("IWANTQUESTS_SMOKE screenshot: {}", message.getString()));
    }

    private ClientSmokeTest() {}
}
