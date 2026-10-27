package dev.iwantquests.testing;

import dev.iwantquests.IWantQuests;
import dev.iwantquests.QuestConfig;
import dev.iwantquests.client.ClientQuestState;
import dev.iwantquests.client.QuestBookScreen;
import dev.iwantquests.engine.ServerQuests;
import dev.iwantquests.quest.QuestPack.Chapter;
import dev.iwantquests.quest.QuestPack.Quest;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/** Development-only editor check, using actual widgets, networking and server quest files. */
final class EditorSmokeTest {
    private static int stage = -2, ticks;
    private static boolean originalEditorEnabled;
    private static Set<String> originalChapters;
    private static String chapter, quest;
    private static double originalX;
    private static QuestBookScreen authorBook;
    private static int beforeCopies, beforeTemplates;
    private static Set<String> templateIds;
    private static String template;
    private static boolean originallyPinned;

    static boolean tick(Minecraft mc) {
        ticks++;
        if (ticks > 400) throw new IllegalStateException("Editor smoke timed out at stage " + stage);
        if (stage > 0 && (ticks < 12 || ClientQuestState.editsInFlight > 0)) return false;
        switch (stage) {
            case -2 -> {
                mc.getSingleplayerServer().execute(() -> {
                    originalEditorEnabled = QuestConfig.EDITOR_ENABLED.get();
                    QuestConfig.EDITOR_ENABLED.set(false);
                    ServerQuests.get(mc.getSingleplayerServer())
                            .syncEditor(
                                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                });
            }
            case -1 -> {
                if (ticks < 12 || ClientQuestState.editor.enabled()) return false;
                QuestBookScreen book = new QuestBookScreen();
                mc.gui.setScreen(book);
                book.toggleEditing();
                require(!book.editing(), "Disabled editor cannot be opened by the world owner");
                mc.getSingleplayerServer().execute(() -> {
                    QuestConfig.EDITOR_ENABLED.set(true);
                    ServerQuests.get(mc.getSingleplayerServer())
                            .syncEditor(
                                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                });
            }
            case 0 -> {
                if (ticks < 12 || !ClientQuestState.editor.enabled()) return false;
                require(ClientQuestState.editor.enabled(), "Singleplayer owner has editor permission");
                originalChapters = ClientQuestState.draftPack.chapters().stream()
                        .map(Chapter::id)
                        .collect(Collectors.toSet());
                mc.getWindow().setWindowed(1280, 800);
                mc.options.guiScale().set(2);
                mc.resizeGui();
                QuestBookScreen book = open(mc);
                Button create = book.children().stream()
                        .filter(w -> w instanceof Button b
                                && b.visible
                                && b.getMessage().getString().equals("New chapter"))
                        .map(w -> (Button) w)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Editor controls: "
                                + book.children().stream()
                                        .filter(w -> w instanceof Button)
                                        .map(w -> ((Button) w).getMessage().getString())
                                        .toList()));
                click(book, create.getX() + 2, create.getY() + 2);
            }
            case 1 -> {
                require(ClientQuestState.editOk, "Chapter creation accepted");
                chapter = ClientQuestState.draftPack.chapters().stream()
                        .map(Chapter::id)
                        .filter(id -> !originalChapters.contains(id))
                        .findFirst()
                        .orElseThrow();
                require(
                        ClientQuestState.pack.chapters().stream().noneMatch(c -> c.id().equals(chapter)),
                        "Draft chapter stays out of the published book");
                EditBox title = title(book(mc));
                book(mc).setFocused(title);
                title.setValue("Editor smoke draft");
                ClientQuestState.action("check", "", "");
            }
            case 2 -> {
                require(
                        title(book(mc)).getValue().equals("Editor smoke draft"),
                        "State sync preserves unfinished text");
                require(title(book(mc)).isFocused(), "State sync preserves keyboard focus");
                mc.getWindow().setWindowed(854, 480);
            }
            case 3 -> {
                require(title(book(mc)).getValue().equals("Editor smoke draft"), "Resize preserves unfinished text");
                require(title(book(mc)).isFocused(), "Resize preserves keyboard focus");
                capture(mc, "editor-narrow.png");
                key(book(mc), GLFW.GLFW_KEY_ENTER, 0);
            }
            case 4 -> {
                require(chapter().title().equals("Editor smoke draft"), "Enter saves chapter title to the server");
                mc.getWindow().setWindowed(1280, 800);
            }
            case 5 -> {
                key(book(mc), GLFW.GLFW_KEY_A, 0);
                require(
                        book(mc).editor().tool() == dev.iwantquests.client.QuestEditing.Tool.ADD,
                        "Add shortcut changes tool");
                click(book(mc), 240, 180);
            }
            case 6 -> {
                require(ClientQuestState.editOk, "Canvas creates quest");
                quest = ClientQuestState.draftPack.quests().stream()
                        .filter(q -> q.chapter().equals(chapter))
                        .map(Quest::id)
                        .findFirst()
                        .orElseThrow();
                require(book(mc).editor().selection().contains(quest), "Created quest is selected");
                originalX = quest().x();
                EditBox title = title(book(mc));
                book(mc).setFocused(title);
                title.setValue("Client/server editor smoke");
                key(book(mc), GLFW.GLFW_KEY_ENTER, 0);
                key(book(mc), GLFW.GLFW_KEY_RIGHT, 0);
                book(mc).onClose();
            }
            case 7 -> {
                require(quest().title().equals("Client/server editor smoke"), "Quest title saved");
                require(quest().x() == originalX + 8, "Closing flushes keyboard movement immediately");
                QuestBookScreen book = open(mc);
                book.focusQuest(quest);
            }
            case 8 -> {
                capture(mc, "editor-quest.png");
                key(book(mc), GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
            }
            case 9 -> {
                require(ClientQuestState.editOk && quest().x() == originalX, "Undo restores node position");
                key(book(mc), GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL);
            }
            case 10 -> {
                require(ClientQuestState.editOk && quest().x() == originalX + 8, "Redo restores node position");
                EditBox title = title(book(mc));
                book(mc).setFocused(title);
                title.setValue("Saved on close");
                book(mc).onClose();
            }
            case 11 -> {
                require(quest().title().equals("Saved on close"), "Closing saves unfinished inspector fields");
                QuestBookScreen book = open(mc);
                book.focusQuest(quest);
                pressButton(book, "Rewards");
            }
            case 12 -> {
                pressButton(book(mc), "+ Item");
            }
            case 13 -> {
                require(quest().rewards().size() == 1, "Item reward added from inspector");
                pressButton(book(mc), "+ XP");
            }
            case 14 -> {
                require(
                        quest().rewards().size() == 2
                                && quest().rewards().getLast().type().equals("xp"),
                        "XP reward added from inspector");
                capture(mc, "editor-rewards.png");
                pressButton(book(mc), "Tasks");
                pressButton(book(mc), "+ Task");
            }
            case 15 -> {
                require(quest().objectives().size() == 2, "Task added from inspector");
                Button obtain = revealButton(book(mc), "Obtain");
                EditBox count = book(mc).children().stream()
                        .filter(w -> w instanceof EditBox b
                                && b.visible
                                && b.getY() == obtain.getY()
                                && b.getX() > obtain.getX())
                        .map(w -> (EditBox) w)
                        .findFirst()
                        .orElseThrow();
                book(mc).setFocused(count);
                count.setValue("8");
                key(book(mc), GLFW.GLFW_KEY_ENTER, 0);
            }
            case 16 -> {
                require(quest().objectives().getLast().count() == 8, "Task count saved from numeric inspector field");
                book(mc).mouseScrolled(book(mc).width - 10, book(mc).height / 2.0, 0, -100);
                capture(mc, "editor-tasks-rewards.png");
                mc.getWindow().setWindowed(854, 480);
            }
            case 17 -> {
                capture(mc, "editor-tasks-narrow.png");
                mc.getWindow().setWindowed(1280, 800);
                pressButton(book(mc), "Details");
                EditBox title = title(book(mc));
                book(mc).setFocused(title);
                title.setValue("Pending movement");
                key(book(mc), GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL);
                key(book(mc), GLFW.GLFW_KEY_RIGHT, 0);
            }
            case 18 -> {
                require(
                        quest().x() == originalX + 16,
                        "Another edit acknowledgement does not discard pending keyboard movement");
                key(book(mc), GLFW.GLFW_KEY_A, 0);
                MouseButtonEvent event = new MouseButtonEvent(350, 150, new MouseButtonInfo(0, GLFW.GLFW_MOD_SHIFT));
                book(mc).mouseClicked(event, false);
                book(mc).mouseReleased(event);
            }
            case 19 -> {
                Quest child = ClientQuestState.draftPack.quests().stream()
                        .filter(q -> q.chapter().equals(chapter) && !q.id().equals(quest))
                        .findFirst()
                        .orElseThrow();
                require(
                        child.prerequisites().equals(java.util.List.of(quest)),
                        "Shift-add links the new quest to the selected quest");
                capture(mc, "editor-linked.png");
            }
            case 20 -> {
                book(mc).focusQuest(quest);
                EditBox icon = book(mc).children().stream()
                        .filter(w -> w instanceof EditBox b
                                && b.visible
                                && b.getValue().equals("minecraft:book"))
                        .map(w -> (EditBox) w)
                        .findFirst()
                        .orElseThrow();
                book(mc).setFocused(icon);
                icon.setValue("minecraft:iwantquests_missing_item");
                key(book(mc), GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL);
            }
            case 21 -> {
                require(
                        !ClientQuestState.editOk && quest().icon().equals("minecraft:book"),
                        "Invalid icon is rejected without changing the quest");
                EditBox icon = book(mc).children().stream()
                        .filter(w -> w instanceof EditBox b
                                && b.visible
                                && b.getValue().equals("minecraft:iwantquests_missing_item"))
                        .map(w -> (EditBox) w)
                        .findFirst()
                        .orElseThrow();
                capture(mc, "editor-validation.png");
                book(mc).setFocused(icon);
                icon.setValue("minecraft:stone");
                key(book(mc), GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL);
            }
            case 22 -> {
                require(
                        ClientQuestState.editOk && quest().icon().equals("minecraft:stone"),
                        "Rejected input can be corrected and saved");
            }
            case 23 -> {
                authorBook = book(mc);
                mc.gui.setScreen(new dev.iwantquests.client.ItemPickerScreen(authorBook, true, value -> {
                    com.google.gson.JsonObject patch = new com.google.gson.JsonObject();
                    patch.addProperty("icon", value);
                    ClientQuestState.edit("patch", "{\"quests\":[{\"id\":\"" + quest + "\",\"set\":" + patch + "}]}");
                }));
                editBox(mc).setValue("oak log");
            }
            case 24 -> {
                capture(mc, "creator-item-picker.png");
                mc.getWindow().setWindowed(854, 480);
                pressScreenButton(mc, "Oak Log");
            }
            case 25 -> {
                require(quest().icon().equals("minecraft:oak_log"), "Visual picker saves selected item");
                mc.gui.setScreen(new dev.iwantquests.client.ItemPickerScreen(authorBook, true, value -> {}));
                pressScreenButton(mc, "Items");
                editBox(mc).setValue("minecraft:logs");
            }
            case 26 -> {
                capture(mc, "creator-tag-picker.png");
                mc.getWindow().setWindowed(1280, 800);
                require(
                        mc.gui.screen().children().stream()
                                .anyMatch(w -> w instanceof Button b
                                        && b.getMessage().getString().startsWith("#minecraft:logs")),
                        "Tag browser lists matching tags");
                mc.gui.screen().onClose();
                authorBook.focusQuest(quest);
                beforeTemplates = ClientQuestState.templates.size();
                templateIds =
                        ClientQuestState.templates.stream().map(t -> t.id()).collect(Collectors.toSet());
                authorBook.editor().saveTemplate();
            }
            case 27 -> {
                require(ClientQuestState.templates.size() == beforeTemplates + 1, "Template saved to server library");
                template = ClientQuestState.templates.stream()
                        .filter(t -> !templateIds.contains(t.id()))
                        .findFirst()
                        .orElseThrow()
                        .id();
                beforeCopies = ClientQuestState.draftPack.quests().size();
                authorBook.setFocused(null);
                key(authorBook, GLFW.GLFW_KEY_D, GLFW.GLFW_MOD_CONTROL);
            }
            case 28 -> {
                require(ClientQuestState.draftPack.quests().size() == beforeCopies + 1, "Ctrl+D duplicates selection");
                authorBook.focusQuest(quest);
                key(authorBook, GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
                key(authorBook, GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL);
            }
            case 29 -> {
                require(
                        ClientQuestState.draftPack.quests().size() == beforeCopies + 2,
                        "Ctrl+C and Ctrl+V copy and paste selection");
                mc.gui.setScreen(new dev.iwantquests.client.TemplateScreen(authorBook));
            }
            case 30 -> {
                capture(mc, "creator-template-library.png");
                mc.getWindow().setWindowed(854, 480);
                pressScreenButton(mc, quest().title());
            }
            case 31 -> {
                require(
                        ClientQuestState.draftPack.quests().size() == beforeCopies + 3,
                        "Reusable template places a quest");
                mc.gui.setScreen(new QuestBookScreen(authorBook));
            }
            case 32 -> {
                require(book(mc).previewing(), "Player preview opened without editor tools");
                require(book(mc).editor() == null, "Preview has no authoring tools");
                capture(mc, "creator-player-preview.png");
                book(mc).onClose();
                mc.getWindow().setWindowed(1280, 800);
                mc.gui.setScreen(new dev.iwantquests.client.PublishScreen(authorBook));
            }
            case 33 -> {
                capture(mc, "creator-publish-review.png");
                mc.getWindow().setWindowed(854, 480);
                require(ClientQuestState.draft.review().added().size() >= 5, "Review lists unpublished additions");
                pressScreenButton(mc, "Publish…");
            }
            case 34 -> {
                require(
                        mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen,
                        "Publishing requires a final review confirmation");
                pressScreenButton(mc, "Yes");
            }
            case 35 -> {
                require(
                        ClientQuestState.editOk
                                && ClientQuestState.pack.chapters().stream().anyMatch(c -> c.id().equals(chapter)),
                        "Publish updates the real book");
                require(
                        !ClientQuestState.draft.review().changedAnything(),
                        "Publication starts a fresh draft baseline");
                QuestBookScreen player = new QuestBookScreen();
                mc.gui.setScreen(player);
                mc.gui.setScreen(new dev.iwantquests.client.QuestSearchScreen(player));
                editBox(mc).setValue("first_steps");
            }
            case 36 -> {
                capture(mc, "creator-quest-search.png");
                mc.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                originallyPinned = dev.iwantquests.client.QuestPins.pinned("iwantquests:first_steps");
                if (!originallyPinned) pressButton(book(mc), "Pin to HUD");
            }
            case 37 -> {
                dev.iwantquests.client.QuestPins.reload();
                require(
                        dev.iwantquests.client.QuestPins.pinned("iwantquests:first_steps"),
                        "Quest can be pinned from its details");
                book(mc).onClose();
            }
            case 38 -> {
                capture(mc, "creator-pinned-hud.png");
                if (!originallyPinned) dev.iwantquests.client.QuestPins.toggle("iwantquests:first_steps");
                mc.gui.setScreen(authorBook);
                ClientQuestState.edit("delete_template", "{\"id\":\"" + template + "\"}");
                ClientQuestState.edit("delete", "{\"chapters\":[\"" + chapter + "\"]}");
            }
            case 39 -> {
                require(
                        ClientQuestState.editOk
                                && ClientQuestState.draftPack.chapters().stream()
                                        .noneMatch(c -> c.id().equals(chapter)),
                        "Smoke content removed from draft");
                ClientQuestState.edit("publish", "{}");
            }
            case 40 -> {
                require(
                        ClientQuestState.editOk
                                && ClientQuestState.pack.chapters().stream().noneMatch(c -> c.id().equals(chapter)),
                        "Cleanup publication removes smoke content");
                mc.getSingleplayerServer().execute(() -> QuestConfig.EDITOR_ENABLED.set(false));
            }
            case 41 -> {
                if (ClientQuestState.editor.enabled() || authorBook.editing()) return false;
                mc.getSingleplayerServer().execute(() -> QuestConfig.EDITOR_ENABLED.set(originalEditorEnabled));
                IWantQuests.LOGGER.info(
                        "IWANTQUESTS_SMOKE: creator pickers, tags, templates, copy/paste, isolated drafts, preview, publish review, search, pinned HUD and cleanup passed");
                return true;
            }
            default -> throw new IllegalStateException("Unknown editor smoke stage");
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static QuestBookScreen open(Minecraft mc) {
        QuestBookScreen book = new QuestBookScreen();
        mc.gui.setScreen(book);
        book.toggleEditing();
        require(book.editing(), "Editor opened");
        return book;
    }

    private static QuestBookScreen book(Minecraft mc) {
        return (QuestBookScreen) mc.gui.screen();
    }

    private static EditBox title(QuestBookScreen book) {
        return book.children().stream()
                .filter(w -> w instanceof EditBox b && b.visible)
                .map(w -> (EditBox) w)
                .findFirst()
                .orElseThrow();
    }

    private static Quest quest() {
        return ClientQuestState.draftPack.quests().stream()
                .filter(q -> q.id().equals(quest))
                .findFirst()
                .orElseThrow();
    }

    private static Chapter chapter() {
        return ClientQuestState.draftPack.chapters().stream()
                .filter(c -> c.id().equals(chapter))
                .findFirst()
                .orElseThrow();
    }

    private static void click(QuestBookScreen book, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        book.mouseClicked(event, false);
        book.mouseReleased(event);
    }

    private static void key(QuestBookScreen book, int key, int modifiers) {
        book.keyPressed(new KeyEvent(key, 0, modifiers));
    }

    private static Button revealButton(QuestBookScreen book, String label) {
        book.mouseScrolled(book.width - 10, book.height / 2.0, 0, 100);
        for (int i = 0; i < 80; i++) {
            var match = book.children().stream()
                    .filter(w -> w instanceof Button b
                            && b.visible
                            && b.getMessage().getString().equals(label))
                    .map(w -> (Button) w)
                    .findFirst();
            if (match.isPresent()) return match.get();
            book.mouseScrolled(book.width - 10, book.height / 2.0, 0, -1);
        }
        throw new IllegalStateException("Inspector button not reachable: " + label);
    }

    private static void pressButton(QuestBookScreen book, String label) {
        Button button = revealButton(book, label);
        click(book, button.getX() + 2, button.getY() + 2);
    }

    private static EditBox editBox(Minecraft mc) {
        return mc.gui.screen().children().stream()
                .filter(w -> w instanceof EditBox b && b.visible)
                .map(w -> (EditBox) w)
                .findFirst()
                .orElseThrow();
    }

    private static void pressScreenButton(Minecraft mc, String label) {
        Button b = mc.gui.screen().children().stream()
                .filter(w -> w instanceof Button button
                        && button.visible
                        && button.active
                        && button.getMessage().getString().equals(label))
                .map(w -> (Button) w)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing screen button: " + label));
        MouseButtonEvent e = new MouseButtonEvent(b.getX() + 2, b.getY() + 2, new MouseButtonInfo(0, 0));
        mc.gui.screen().mouseClicked(e, false);
        mc.gui.screen().mouseReleased(e);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(
                mc.gameDirectory,
                name,
                mc.gameRenderer.mainRenderTarget(),
                1,
                message -> IWantQuests.LOGGER.info("IWANTQUESTS_SMOKE screenshot: {}", message.getString()));
    }

    private EditorSmokeTest() {}
}
