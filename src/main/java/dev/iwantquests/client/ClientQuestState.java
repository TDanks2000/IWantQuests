package dev.iwantquests.client;

import dev.iwantquests.network.*;
import dev.iwantquests.quest.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClientQuestState {
    public static QuestPack pack;
    public static QuestPack draftPack;
    public static DraftInfo draft;
    public static java.util.List<dev.iwantquests.edit.QuestTemplates.Template> templates = List.of();
    public static PlayerSnapshot snapshot;
    public static String feedback = "";
    /** Increases for every server result message, so the screen can tell a repeated message from an old one. */
    public static int feedbackSerial;
    /** Latest team management view; null until requested with the team_info action. */
    public static TeamInfo team;

    public static int revision;
    /** Editing permission and editable IDs, as last sent by the server. Never trusted by the server itself. */
    public static EditorInfo editor = EditorInfo.NONE;

    public static Set<String> editableQuests = Set.of(), editableChapters = Set.of();
    /** Increases for every editor result. {@code editSelect} is the quest or chapter that request created, if any. */
    public static int editResultSerial;

    public static boolean editOk;
    public static String editSelect;
    /** Editor requests sent and not yet answered; positions the screen shows optimistically stay until this is zero. */
    public static int editsInFlight;

    private static final SyncAssembler ASSEMBLER = new SyncAssembler();

    public static void receive(SyncPayload received) {
        SyncAssembler.Part whole = ASSEMBLER.accept(received.toPart());
        if (whole == null) return;
        SyncPayload payload = new SyncPayload(whole.kind(), whole.json());
        switch (payload.kind()) {
            case "definitions" -> pack = QuestLoader.JSON.fromJson(payload.json(), QuestPack.class);
            case "draft_definitions" -> draftPack = QuestLoader.JSON.fromJson(payload.json(), QuestPack.class);
            case "draft_info" -> draft = QuestLoader.JSON.fromJson(payload.json(), DraftInfo.class);
            case "templates" ->
                templates = List.of(QuestLoader.JSON.fromJson(
                        payload.json(), dev.iwantquests.edit.QuestTemplates.Template[].class));
            case "state" -> snapshot = QuestLoader.JSON.fromJson(payload.json(), PlayerSnapshot.class);
            case "delta" -> {
                PlayerSnapshot delta = QuestLoader.JSON.fromJson(payload.json(), PlayerSnapshot.class);
                Map<String, PlayerSnapshot.QuestView> views = new LinkedHashMap<>();
                if (snapshot != null && snapshot.team().equals(delta.team())) views.putAll(snapshot.quests());
                views.putAll(delta.quests());
                snapshot =
                        new PlayerSnapshot(delta.team(), delta.teamName(), delta.members(), views, delta.memberList());
            }
            case "team" -> receiveTeam(QuestLoader.JSON.fromJson(payload.json(), TeamInfo.class));
            case "editor" -> {
                editor = QuestLoader.JSON.fromJson(payload.json(), EditorInfo.class);
                editableQuests = Set.copyOf(editor.quests());
                editableChapters = Set.copyOf(editor.chapters());
                if (!editor.enabled()) {
                    draftPack = null;
                    draft = null;
                    templates = List.of();
                }
            }
            case "edit_result" -> {
                EditResult result = QuestLoader.JSON.fromJson(payload.json(), EditResult.class);
                editsInFlight = Math.max(0, editsInFlight - 1);
                editOk = result.ok();
                editSelect = result.select();
                feedback = result.message();
                feedbackSerial++;
                editResultSerial++;
            }
            case "result" -> {
                feedback = QuestLoader.JSON.fromJson(payload.json(), String.class);
                feedbackSerial++;
            }
            case "open" -> Minecraft.getInstance().gui.setScreen(new QuestBookScreen());
        }
        revision++;
    }
    /** Shows a toast when a new invitation arrives; refreshes of the same invitation (new expiry) stay quiet. */
    private static void receiveTeam(TeamInfo next) {
        TeamInfo.Invite before = team == null ? null : team.invite();
        team = next;
        TeamInfo.Invite now = next.invite();
        if (now != null && !sameInvitation(before, now)) {
            SystemToast.add(
                    Minecraft.getInstance().gui.toastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("iwantquests.ui.team.toast_title"),
                    Component.translatable("iwantquests.ui.team.toast_body", now.from(), now.teamName()));
        }
    }

    private static boolean sameInvitation(TeamInfo.Invite a, TeamInfo.Invite b) {
        return a != null && Objects.equals(a.from(), b.from()) && Objects.equals(a.teamName(), b.teamName());
    }

    public static void reset() {
        QuestPins.reload();
        ASSEMBLER.reset();
        pack = null;
        draftPack = null;
        draft = null;
        templates = List.of();
        snapshot = null;
        team = null;
        feedback = "";
        editor = EditorInfo.NONE;
        editableQuests = Set.of();
        editableChapters = Set.of();
        editsInFlight = 0;
        editSelect = null;
        revision++;
    }
    /** Sends an editor request; the server answers with an {@code edit_result}. */
    public static void edit(String op, String json) {
        if (!editor.enabled()) return;
        if (json.length() > EditPayload.MAX_JSON) {
            feedback = dev.iwantquests.client.ui.QuestText.t("editor.selection_large");
            feedbackSerial++;
            return;
        }
        editsInFlight++;
        ClientPacketDistributor.sendToServer(new EditPayload(op, json));
    }

    public static void action(String action, String quest, String objective) {
        ClientPacketDistributor.sendToServer(new ActionPayload(action, quest, objective));
    }

    private ClientQuestState() {}
}
