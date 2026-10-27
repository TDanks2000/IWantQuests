package dev.iwantquests.client;

import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.client.theme.QuestTheme.Anchor;
import dev.iwantquests.client.theme.ThemeManager;
import dev.iwantquests.client.ui.*;
import dev.iwantquests.network.PlayerSnapshot.Member;
import dev.iwantquests.network.PlayerSnapshot.QuestView;
import dev.iwantquests.network.TeamInfo;
import dev.iwantquests.quest.QuestPack;
import dev.iwantquests.quest.QuestPack.Chapter;
import dev.iwantquests.quest.QuestPack.Objective;
import dev.iwantquests.quest.QuestPack.Quest;
import dev.iwantquests.quest.QuestPack.Reward;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The quest book. It is deliberately minimal: a chapter list, a quest tree, and a few floating controls.
 * Every colour, sprite, shape, anchor and size comes from the current chapter's {@link QuestTheme}, and every
 * string from the language files, so a pack can restyle all of it. Layout is derived from the window size and
 * the selection on each call, so nothing is cached between frames.
 */
public final class QuestBookScreen extends Screen {
    private static final int PAD = 8, ICON = 16, GAP = 6, BTN = 18, ZOOM_BTN = 16;
    private static final int FACE = 10, FACE_GAP = 2, MAX_FACES = 4;
    private static final int TAB_GAP = 2, SIDEBAR_FOOTER = 28;
    private static final int CONTENT_PAD = 12;
    private static final int HEAD_ICON = 24, HEAD_TEXT = 30, ROW = 22, SECTION = 16, ACTION = 18;
    private static final int NAV = 16, NAV_GAP = 4;
    private static final int FEEDBACK_TICKS = 90, FEEDBACK_FADE = 20;

    final CanvasTransform canvas = new CanvasTransform();
    private final Map<String, ItemStack> icons = new HashMap<>();
    final QuestText qt = new QuestText(this::theme);
    String chapter;
    private String selected;
    private int seenRevision = -1, ticks, detailScroll, chapterOffset, seenFeedback, feedbackSince = -1000;
    private boolean dragging, fitPending = true, teamRequested, chaptersExpanded;
    private @Nullable QuestEditing editing;
    private final @Nullable QuestBookScreen previewParent;
    /** The option the player picked for each choice reward of a quest, by quest ID; -1 means not picked yet. */
    private final Map<String, int[]> picks = new HashMap<>();

    /** Vertical offsets, relative to the top of the details content (before scrolling). */
    private record DetailLayout(
            int statusY,
            int descriptionY,
            int objectivesY,
            int[] objectiveY,
            int contributionsY,
            int requiresY,
            int[] prerequisiteY,
            int rewardsY,
            int[] rewardY,
            int claimY,
            int pinY,
            int height) {}

    /** The row of team and close controls, left to right: invitation, reward notice, faces, Team button, close button. */
    private record Actions(
            int y,
            int inviteX,
            int inviteWidth,
            int noticeX,
            int noticeWidth,
            int facesX,
            int facesWidth,
            int editX,
            int editWidth,
            int teamX,
            int teamWidth,
            int closeX,
            int left,
            int width) {}

    public QuestBookScreen() {
        this(null);
    }

    public QuestBookScreen(@Nullable QuestBookScreen previewParent) {
        super(Component.literal("IWantQuests"));
        this.previewParent = previewParent;
        if (previewParent != null) chapter = previewParent.chapter;
    }

    public boolean previewing() {
        return previewParent != null;
    }

    QuestPack definitions() {
        return (editing != null || previewing()) && ClientQuestState.draftPack != null
                ? ClientQuestState.draftPack
                : ClientQuestState.pack;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void onClose() {
        if (previewParent != null) minecraft.gui.setScreen(previewParent);
        else super.onClose();
    }

    @Override
    public void removed() {
        if (editing != null) editing.savePending();
        super.removed();
    }

    // ---------------------------------------------------------------- data

    List<Chapter> chapters() {
        if (definitions() == null) return List.of();
        return QuestViews.list(definitions().chapters()).stream()
                .sorted(Comparator.comparingInt(Chapter::order))
                .toList();
    }

    List<Quest> quests() {
        return definitions() == null ? List.of() : QuestViews.list(definitions().quests());
    }

    @Nullable
    Chapter currentChapter() {
        return chapters().stream()
                .filter(c -> c.id().equals(chapter))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    Quest findQuest(String id) {
        return quests().stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
    }

    QuestTheme theme() {
        Chapter c = currentChapter();
        return ThemeManager.get(c == null ? null : c.theme());
    }

    List<Quest> visibleQuests() {
        if (chapter == null) return List.of();
        return quests().stream()
                .filter(q -> chapter.equals(q.chapter()))
                // The editor shows hidden quests too, otherwise they could not be found and changed.
                .filter(q -> editing != null || !q.hidden() || view(q).available() || view(q).completed())
                .toList();
    }

    private @Nullable Quest current() {
        return visibleQuests().stream()
                .filter(q -> q.id().equals(selected))
                .findFirst()
                .orElse(null);
    }

    private boolean detailsOpen() {
        return editing == null && current() != null;
    }

    QuestView view(Quest q) {
        if (previewing())
            return new QuestView(Map.of(), false, false, q.prerequisites().isEmpty(), Map.of());
        var snapshot = ClientQuestState.snapshot;
        return QuestViews.view(
                snapshot == null || snapshot.quests() == null
                        ? null
                        : snapshot.quests().get(q.id()));
    }

    private List<Member> memberList() {
        var snapshot = ClientQuestState.snapshot;
        return snapshot == null ? List.of() : QuestViews.list(snapshot.memberList());
    }

    /** Required quests of a chapter: how many there are and how many are complete. Optional quests are excluded. */
    private int[] chapterStats(Chapter c) {
        int done = 0, total = 0;
        for (Quest q : quests()) {
            if (!c.id().equals(q.chapter()) || q.optional()) continue;
            total++;
            if (view(q).completed()) done++;
        }
        return new int[] {done, total};
    }

    private List<String> unmetPrerequisites(Chapter c) {
        List<String> unmet = new ArrayList<>();
        for (String id : c.prerequisitesOrEmpty()) {
            Quest prerequisite = findQuest(id);
            if (prerequisite != null && !view(prerequisite).completed()) unmet.add(prerequisite.title());
        }
        return unmet;
    }

    private List<Quest> readyQuests() {
        return quests().stream()
                .filter(q -> view(q).completed() && !view(q).claimed())
                .toList();
    }

    private boolean chapterReady(Chapter c) {
        return quests().stream().anyMatch(q -> c.id().equals(q.chapter()) && view(q).completed() && !view(q).claimed());
    }

    private void clampChapterOffset() {
        int max = Math.max(0, chapters().size() - tabRows());
        chapterOffset = Math.max(0, Math.min(chapterOffset, max));
    }

    // ---------------------------------------------------------------- layout

    private boolean sidebarShown() {
        return theme().sidebarPosition != Anchor.HIDDEN;
    }

    private boolean sidebarOnRight() {
        return theme().sidebarPosition == Anchor.RIGHT;
    }

    private int sidebarWidth() {
        if (!sidebarShown()) return 0;
        return chaptersExpanded ? Math.min(theme().sidebarExpandedWidth, width / 3) : theme().sidebarWidth;
    }

    private int sidebarX() {
        return sidebarOnRight() ? width - sidebarWidth() : 0;
    }
    /** The editor's panel is docked to the side opposite the chapter list. */
    private int inspectorWidth() {
        return editing == null ? 0 : QuestInspector.width(width);
    }

    int inspectorX() {
        return sidebarOnRight() ? 0 : width - inspectorWidth();
    }

    int canvasLeft() {
        return sidebarOnRight() ? inspectorWidth() : sidebarWidth();
    }

    int canvasRight() {
        return sidebarOnRight() ? width - sidebarWidth() : width - inspectorWidth();
    }

    private int headerHeight() {
        return theme().headerHeight;
    }

    int treeTop() {
        return headerHeight();
    }

    private double centreX() {
        return (canvasLeft() + canvasRight()) / 2.0;
    }

    private double centreY() {
        return (treeTop() + 36 + height - 24) / 2.0;
    }

    void revealQuest(Quest quest) {
        double sx = canvas.screenX(quest.x()), sy = canvas.screenY(quest.y());
        if (sx < canvasLeft() + 104 || sx > canvasRight() - 24 || sy < treeTop() + 28 || sy > height - 36) {
            double targetX = (canvasLeft() + 104 + canvasRight() - 24) / 2.0;
            canvas.panX = targetX - quest.x() * canvas.zoom;
            canvas.panY = centreY() - quest.y() * canvas.zoom;
        }
    }

    private int panelWidth() {
        return Math.min(width - 16, Math.max(300, theme().detailsWidth * 2 + 32));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelRight() {
        return panelX() + panelWidth();
    }

    private int panelHeight() {
        Quest q = current();
        int available = height - 16;
        return q == null
                ? available
                : Math.min(available, layoutDetails(q, view(q)).height() + CONTENT_PAD * 2);
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private int panelBottom() {
        return panelTop() + panelHeight();
    }

    private int textX() {
        return panelX() + CONTENT_PAD;
    }

    private int textWidth() {
        return panelWidth() - CONTENT_PAD * 2 - 4;
    }

    private boolean detailColumns() {
        return panelWidth() >= 360;
    }

    private int taskWidth() {
        return detailColumns() ? (textWidth() - 16) / 2 : textWidth();
    }

    private int rewardX() {
        return detailColumns() ? textX() + taskWidth() + 16 : textX();
    }

    private int detailViewTop() {
        return panelTop();
    }

    private int detailViewBottom() {
        return panelBottom();
    }

    private int contentBase() {
        return panelTop() + CONTENT_PAD - detailScroll;
    }

    private int contentY(int relative) {
        return contentBase() + relative;
    }

    private boolean fits(int y, int h) {
        return y >= detailViewTop() && y + h <= detailViewBottom() - CONTENT_PAD / 2;
    }

    private int tabHeight() {
        return theme().tabHeight;
    }

    private int tabPitch() {
        return tabHeight() + TAB_GAP;
    }

    private int tabRows() {
        return Math.max(1, (height - treeTop() - PAD - SIDEBAR_FOOTER) / tabPitch());
    }

    private int tabX() {
        return sidebarX() + (chaptersExpanded ? 4 : (sidebarWidth() - tabWidth()) / 2);
    }

    private int tabWidth() {
        return chaptersExpanded ? sidebarWidth() - 8 : Math.min(tabHeight(), sidebarWidth() - 4);
    }

    private int tabY(int row) {
        return treeTop() + PAD + row * tabPitch();
    }

    /** Top-left of a {@code w} x {@code h} block placed at {@code anchor} inside the canvas. */
    private int[] place(Anchor anchor, int w, int h) {
        int left = canvasLeft() + PAD, right = canvasRight() - PAD - w;
        int centre = (canvasLeft() + canvasRight() - w) / 2;
        int top = headerHeight() > 0 ? (headerHeight() - h) / 2 : PAD;
        int bottom = height - PAD - h;
        return switch (anchor) {
            case TOP_RIGHT -> new int[] {right, top};
            case TOP_CENTER -> new int[] {centre, top};
            case BOTTOM_LEFT -> new int[] {left, bottom};
            case BOTTOM_CENTER -> new int[] {centre, bottom};
            case BOTTOM_RIGHT -> new int[] {right, bottom};
            default -> new int[] {left, top};
        };
    }

    private static boolean topBand(Anchor a) {
        return a == Anchor.TOP_LEFT || a == Anchor.TOP_CENTER || a == Anchor.TOP_RIGHT;
    }

    private static boolean bottomBand(Anchor a) {
        return a == Anchor.BOTTOM_LEFT || a == Anchor.BOTTOM_CENTER || a == Anchor.BOTTOM_RIGHT;
    }

    private Actions actions() {
        QuestTheme t = theme();
        List<Member> members = memberList();
        int shown = editing == null && members.size() > 1 ? Math.min(MAX_FACES, members.size()) : 0;
        int facesWidth = shown == 0 ? 0 : shown * FACE + (shown - 1) * FACE_GAP;
        int teamWidth = Math.min(96, qt.width(teamLabel()) + 14);
        String invite = editing == null ? inviteText() : null;
        String notice = editing != null || readyQuests().isEmpty()
                ? null
                : noticeText(readyQuests().size());
        int inviteWidth = invite == null ? 0 : 10 + 4 + qt.width(invite);
        int noticeWidth = notice == null ? 0 : 10 + 4 + qt.width(notice);
        int editWidth = ClientQuestState.editor.enabled() && !previewing()
                ? Math.max(qt.width(QuestText.t("editor.edit")), qt.width(QuestText.t("editor.done"))) + 14
                : 0;
        int total = 0;
        for (int part : new int[] {inviteWidth, noticeWidth, facesWidth, editWidth, teamWidth, BTN})
            total += part == 0 ? 0 : part + GAP;
        total -= GAP;
        int[] origin = place(t.actionsPosition, total, BTN);
        int x = origin[0];
        int inviteX = x;
        x += inviteWidth == 0 ? 0 : inviteWidth + GAP;
        int noticeX = x;
        x += noticeWidth == 0 ? 0 : noticeWidth + GAP;
        int facesX = x;
        x += facesWidth == 0 ? 0 : facesWidth + GAP;
        int editX = x;
        x += editWidth == 0 ? 0 : editWidth + GAP;
        int teamX = x;
        int closeX = teamX + teamWidth + GAP;
        return new Actions(
                origin[1],
                inviteX,
                inviteWidth,
                noticeX,
                noticeWidth,
                facesX,
                facesWidth,
                editX,
                editWidth,
                teamX,
                teamWidth,
                closeX,
                origin[0],
                total);
    }

    /** Zoom row, left to right: Check, minus, plus, Fit. Shared by widget placement and hit tests. */
    private int[] zoomRow() {
        int check = qt.width(QuestText.t("check")) + 14, fit = qt.width(QuestText.t("fit")) + 14;
        int total = check + ZOOM_BTN * 2 + fit + 3 * 3;
        int[] origin = place(theme().controlsPosition, total, ZOOM_BTN);
        int x = origin[0];
        int[] row = new int[] {
            x, x + check + 3, x + check + 3 + ZOOM_BTN + 3, x + check + 3 + ZOOM_BTN * 2 + 6, origin[1], check, fit
        };
        return row;
    }

    int nodeSize(QuestTheme t, Quest q) {
        return Math.max(4, (int) Math.round(
                t.nodeSize * q.sizeOrDefault() * t.shape(q.shapeOrDefault()).scale() * canvas.zoom));
    }

    int nodeCentreX(Quest q) {
        return (int) Math.round(canvas.screenX(editing == null ? q.x() : editing.x(q)));
    }

    int nodeCentreY(Quest q) {
        return (int) Math.round(canvas.screenY(editing == null ? q.y() : editing.y(q)));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        if (!teamRequested) {
            teamRequested = true;
            chaptersExpanded = width >= 520 && chapters().size() > 1;
            ClientQuestState.action("team_info", "", "");
        }
        widgets();
    }

    @Override
    protected void repositionElements() {
        if (editing != null && width < 520) chaptersExpanded = false;
        fitPending = true;
        widgets();
    }

    private void widgets() {
        clearWidgets();
        if (chapter == null && !chapters().isEmpty())
            chapter = chapters().getFirst().id();
        clampChapterOffset();
        if (detailsOpen()) {
            addDetailWidgets();
            return;
        }
        Actions a = actions();
        if (a.editWidth() > 0) {
            var edit = addRenderableWidget(new QuestButton(
                    this::theme,
                    a.editX(),
                    a.y(),
                    a.editWidth(),
                    BTN,
                    QuestText.t(editing == null ? "editor.edit" : "editor.done"),
                    b -> toggleEditing()));
            edit.primary = editing != null;
            edit.setTooltip(Tooltip.create(Component.literal(QuestText.t("editor.edit_tip"))));
        }
        var team = addRenderableWidget(new QuestButton(
                this::theme, a.teamX(), a.y(), a.teamWidth(), BTN, clippedTeamLabel(a.teamWidth()), b -> openTeam()));
        team.active = !previewing();
        addRenderableWidget(
                new QuestButton(this::theme, a.closeX(), a.y(), BTN, BTN, QuestText.t("close_glyph"), b -> onClose()));
        if (sidebarShown()) {
            int size = 16;
            int x = sidebarX() + (sidebarWidth() - size) / 2;
            var toggle = addRenderableWidget(new QuestButton(
                    this::theme,
                    x,
                    height - PAD - size,
                    size,
                    size,
                    QuestText.t(chaptersExpanded ? "collapse_glyph" : "expand_glyph"),
                    b -> toggleChapters()));
            toggle.setTooltip(Tooltip.create(
                    Component.literal(QuestText.t(chaptersExpanded ? "collapse_chapters" : "expand_chapters"))));
        }
        addCanvasWidgets();
        var search = addRenderableWidget(new QuestButton(
                this::theme,
                canvasLeft() + PAD,
                height - PAD - 18,
                qt.width(QuestText.t("search.button")) + 14,
                18,
                QuestText.t("search.button"),
                b -> minecraft.gui.setScreen(new QuestSearchScreen(this))));
        search.setTooltip(Tooltip.create(Component.literal(QuestText.t("search.tip"))));
        if (editing != null) editing.addWidgets();
    }

    <T extends GuiEventListener & Renderable & NarratableEntry> T addEditorWidget(T widget) {
        return addRenderableWidget(widget);
    }

    void refreshWidgets() {
        widgets();
    }

    /** Starts a canvas pan from the current mouse press. The editor calls this when a press lands on empty canvas. */
    void beginPan() {
        dragging = true;
    }

    boolean inCanvas(double x, double y) {
        return x >= canvasLeft() && x < canvasRight() && y >= treeTop() && y < height;
    }

    /** Switches the quest editor on or off. Only available when the server says the player may edit. Public for the smoke test. */
    public void toggleEditing() {
        if (editing == null) {
            if (!ClientQuestState.editor.enabled()) return;
            selected = null;
            detailScroll = 0;
            editing = new QuestEditing(this);
        } else {
            editing.close();
            editing = null;
        }
        fitPending = true;
        init();
    }

    public boolean editing() {
        return editing != null;
    }

    /** The editor controller; null while the book is in its normal mode. Public for the smoke test. */
    public @Nullable QuestEditing editor() {
        return editing;
    }

    /** Collapses or expands the chapter list. Public for the smoke test. */
    public void toggleChapters() {
        chaptersExpanded = !chaptersExpanded;
        fitPending = true;
        widgets();
    }

    private String clippedTeamLabel(int buttonWidth) {
        return qt.clip(teamLabel(), buttonWidth - 8);
    }

    private void addCanvasWidgets() {
        if (theme().controlsPosition == Anchor.HIDDEN) return;
        int[] z = zoomRow();
        var check = addRenderableWidget(
                new QuestButton(this::theme, z[0], z[4], z[5], ZOOM_BTN, QuestText.t("check"), b -> {
                    if (!previewing()) ClientQuestState.action("check", "", "");
                }));
        check.active = !previewing();
        addRenderableWidget(new QuestButton(
                this::theme, z[1], z[4], ZOOM_BTN, ZOOM_BTN, QuestText.t("zoom_out"), b -> zoomBy(1 / 1.15)));
        addRenderableWidget(new QuestButton(
                this::theme, z[2], z[4], ZOOM_BTN, ZOOM_BTN, QuestText.t("zoom_in"), b -> zoomBy(1.15)));
        addRenderableWidget(new QuestButton(this::theme, z[3], z[4], z[6], ZOOM_BTN, QuestText.t("fit"), b -> fit()));
    }

    private void addDetailWidgets() {
        Quest q = current();
        if (q == null) return;
        QuestView view = view(q);
        DetailLayout layout = layoutDetails(q, view);
        detailScroll = Math.max(0, Math.min(maxDetailScroll(layout), detailScroll));
        int x = textX(), w = taskWidth();
        int navY = panelTop() + 6, right = panelRight() - 6 - NAV;
        addRenderableWidget(
                new QuestButton(this::theme, right, navY, NAV, NAV, QuestText.t("details.close"), b -> closeDetails()));
        addRenderableWidget(new QuestButton(
                this::theme, right - NAV - NAV_GAP, navY, NAV, NAV, QuestText.t("details.next"), b -> cycleQuest(1)));
        addRenderableWidget(new QuestButton(
                this::theme,
                right - (NAV + NAV_GAP) * 2,
                navY,
                NAV,
                NAV,
                QuestText.t("details.previous"),
                b -> cycleQuest(-1)));
        List<Objective> objectives = QuestViews.list(q.objectives());
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            if (!QuestViews.actionable(o)) continue;
            int y = contentY(layout.objectiveY()[i] + ROW + 2);
            if (!fits(y, ACTION)) continue;
            String label = QuestText.t("submit".equals(o.type()) ? "submit" : "manual");
            var button = addRenderableWidget(new QuestButton(
                    this::theme, x, y, w, ACTION, label, b -> ClientQuestState.action(o.type(), q.id(), o.id())));
            button.active = !previewing()
                    && view.available()
                    && !view.completed()
                    && view.objectives().getOrDefault(o.id(), 0) < o.count();
        }
        int[] chosen = picksFor(q, view);
        List<Reward> rewards = QuestViews.list(q.rewards());
        int slot = 0;
        for (int i = 0; i < rewards.size(); i++) {
            if (!"choice".equals(rewards.get(i).type())) continue;
            int choice = slot++;
            List<Reward> options = rewards.get(i).optionsOrEmpty();
            for (int k = 0; k < options.size(); k++) {
                int y = contentY(layout.rewardY()[i] + CHOICE_HEAD + k * (ACTION + 2));
                if (!fits(y, ACTION)) continue;
                int option = k;
                var pick = addRenderableWidget(new QuestButton(
                        this::theme,
                        rewardX() + 20,
                        y,
                        taskWidth() - 20,
                        ACTION,
                        qt.clip(rewardLine(options.get(k)), taskWidth() - 28),
                        b -> {
                            chosen[choice] = option;
                            widgets();
                        }));
                pick.active = !previewing() && view.completed() && !view.claimed();
                pick.primary = chosen[choice] == k;
            }
        }
        boolean picked = Arrays.stream(chosen).allMatch(c -> c >= 0);
        int claimY = contentY(layout.claimY());
        if (fits(claimY, 20)) {
            String choices =
                    Arrays.stream(chosen).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            var claim = addRenderableWidget(new QuestButton(
                    this::theme,
                    rewardX(),
                    claimY,
                    taskWidth(),
                    20,
                    claimLabel(view, picked),
                    b -> ClientQuestState.action("claim", q.id(), choices)));
            claim.active = !previewing() && view.completed() && !view.claimed() && picked;
            claim.primary = claim.active;
        }
        int pinY = contentY(layout.pinY());
        if (fits(pinY, 18) && !previewing())
            addRenderableWidget(new QuestButton(
                    this::theme,
                    textX(),
                    pinY,
                    textWidth(),
                    18,
                    QuestText.t(QuestPins.pinned(q.id()) ? "pins.unpin" : "pins.pin"),
                    b -> {
                        QuestPins.toggle(q.id());
                        widgets();
                    }));
    }

    private static String claimLabel(QuestView view, boolean picked) {
        if (view.claimed()) return QuestText.t("claimed");
        if (!view.completed()) return QuestText.t("claim.locked");
        return QuestText.t(picked ? "claim" : "claim.choose");
    }

    /** The player's picks for this quest's choice rewards, forgotten once the rewards are claimed or the quest restarts. */
    private int[] picksFor(Quest q, QuestView view) {
        int count = (int) QuestViews.list(q.rewards()).stream()
                .filter(r -> "choice".equals(r.type()))
                .count();
        if (!view.completed() || view.claimed()) picks.remove(q.id());
        int[] chosen = picks.get(q.id());
        if (chosen == null || chosen.length != count) {
            chosen = new int[count];
            Arrays.fill(chosen, -1);
            picks.put(q.id(), chosen);
        }
        return chosen;
    }

    public void jumpToQuest(String id) {
        Quest q = findQuest(id);
        if (q == null) return;
        if (!q.chapter().equals(chapter))
            selectChapter(chapters().stream()
                    .filter(c -> c.id().equals(q.chapter()))
                    .findFirst()
                    .orElseThrow());
        focusQuest(id);
    }

    // ---------------------------------------------------------------- navigation and actions

    /** Selects a quest and opens its details without moving the tree. Public for tests. */
    public void focusQuest(String id) {
        if (editing != null) {
            Quest quest = findQuest(id);
            if (quest != null) {
                editing.inspector().commitAll();
                if (!quest.chapter().equals(chapter))
                    selectChapter(chapters().stream()
                            .filter(c -> c.id().equals(quest.chapter()))
                            .findFirst()
                            .orElseThrow());
                editing.select(quest);
            }
            return;
        }
        selected = id;
        detailScroll = 0;
        init();
    }

    /** Opens a quest that may live in another chapter, switching chapters first if needed. */
    private void openQuest(Quest q) {
        if (!q.chapter().equals(chapter)) {
            chapter = q.chapter();
            selected = null;
            fit();
        }
        focusQuest(q.id());
    }

    void selectChapter(Chapter c) {
        if (editing != null) editing.chapterChanged();
        chapter = c.id();
        selected = null;
        detailScroll = 0;
        fitPending = true;
        init();
    }

    private void closeDetails() {
        selected = null;
        detailScroll = 0;
        init();
    }

    void fit() {
        List<Quest> quests = visibleQuests();
        if (quests.isEmpty()) return;
        QuestTheme t = theme();
        double minX = quests.stream()
                .mapToDouble(q -> q.x() - t.nodeSize * q.sizeOrDefault() / 2 - 16)
                .min()
                .orElse(0);
        double maxX = quests.stream()
                .mapToDouble(q -> q.x() + t.nodeSize * q.sizeOrDefault() / 2 + 16)
                .max()
                .orElse(0);
        double minY = quests.stream()
                .mapToDouble(q -> q.y() - t.nodeSize * q.sizeOrDefault() / 2 - 16)
                .min()
                .orElse(0);
        double maxY = quests.stream()
                .mapToDouble(q -> q.y() + t.nodeSize * q.sizeOrDefault() / 2 + 16)
                .max()
                .orElse(0);
        double zoom = Math.min(
                (canvasRight() - canvasLeft() - 24) / (maxX - minX), (height - treeTop() - 60) / (maxY - minY));
        canvas.zoom = Math.max(.25, Math.min(1.5, zoom));
        canvas.panX = centreX() - (minX + maxX) / 2 * canvas.zoom;
        canvas.panY = centreY() - (minY + maxY) / 2 * canvas.zoom;
        fitPending = false;
    }

    private void zoomBy(double factor) {
        canvas.zoomAt(centreX(), centreY(), factor);
    }

    private void cycleQuest(int step) {
        List<Quest> quests = visibleQuests();
        if (quests.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(selected)) index = i;
        }
        index = Math.floorMod(index + step, quests.size());
        focusQuest(quests.get(index).id());
        Quest now = current();
        if (now != null) {
            minecraft.getNarrator().saySystemNow(Component.literal(now.title() + ". " + QuestViews.status(view(now))));
        }
    }

    private void scrollDetails(int delta) {
        Quest q = current();
        if (q == null) return;
        int max = maxDetailScroll(layoutDetails(q, view(q)));
        detailScroll = Math.max(0, Math.min(max, detailScroll + delta));
        widgets();
    }

    private int maxDetailScroll(DetailLayout layout) {
        return Math.max(0, layout.height() + CONTENT_PAD * 2 - (detailViewBottom() - detailViewTop()));
    }

    @Override
    public void tick() {
        ticks++;
        if (seenFeedback != ClientQuestState.feedbackSerial) {
            seenFeedback = ClientQuestState.feedbackSerial;
            feedbackSince = ticks;
        }
        if (editing != null && !ClientQuestState.editor.enabled()) {
            editing.close();
            editing = null;
            init();
        }
        if (editing != null) editing.tick();
        if (seenRevision != ClientQuestState.revision) {
            seenRevision = ClientQuestState.revision;
            icons.clear();
            if (chapters().stream().noneMatch(c -> c.id().equals(chapter))) {
                chapter = null;
                selected = null;
                fitPending = true;
            }
            if (selected != null && current() == null) selected = null;
            if (editing != null) editing.dataChanged();
            init();
        }
        if (fitPending) fit();
    }

    // ---------------------------------------------------------------- item and icon helpers

    ItemStack icon(@Nullable String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        return icons.computeIfAbsent(id, this::resolveItem);
    }

    private ItemStack resolveItem(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void drawQuestIcon(GuiGraphicsExtractor g, Quest q, int x, int y, int size) {
        Identifier texture = QuestDraw.sprite(q.iconTexture());
        if (texture != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, size, size);
        else QuestDraw.itemScaled(g, icon(q.icon()), x, y, size);
    }

    private void drawObjectiveIcon(GuiGraphicsExtractor g, Quest q, Objective o, int x, int y) {
        ItemStack stack = QuestViews.itemTarget(o) ? icon(o.target()) : ItemStack.EMPTY;
        if (stack.isEmpty()) drawQuestIcon(g, q, x, y, ICON);
        else QuestDraw.itemScaled(g, stack, x, y, ICON);
    }

    private static final int CHOICE_HEAD = 14, OPTION_LINE = 11;

    private ItemStack rewardStack(Reward r) {
        ItemStack custom = icon(r.icon());
        if ("item".equals(r.type())) {
            ItemStack base = custom.isEmpty() ? icon(r.target()) : custom;
            return base.isEmpty() ? ItemStack.EMPTY : new ItemStack(base.getItem(), Math.max(1, r.count()));
        }
        if (!custom.isEmpty()) return custom;
        return new ItemStack(
                switch (r.type()) {
                    case "command" -> Items.NETHER_STAR;
                    case "loot_table" -> Items.CHEST;
                    case "random" -> Items.ENDER_CHEST;
                    case "choice" -> Items.TRAPPED_CHEST;
                    default -> Items.EXPERIENCE_BOTTLE;
                });
    }

    /** The reward's own name: the pack's custom name, or one made from its type and target. */
    private String rewardName(Reward r) {
        if (r.name() != null && !r.name().isBlank()) return r.name();
        return switch (r.type()) {
            case "item" -> {
                ItemStack stack = icon(r.target());
                yield stack.isEmpty()
                        ? QuestViews.text(r.target())
                        : stack.getHoverName().getString();
            }
            case "xp" -> QuestText.t("reward.xp", r.count());
            case "command" -> QuestText.t("reward.command");
            case "loot_table" -> QuestText.t("reward.loot_table");
            case "random" -> QuestText.t("reward.random");
            case "choice" -> QuestText.t("reward.choice");
            default -> r.type();
        };
    }

    /** One line describing a reward, with its amount where that is part of the reward. */
    private String rewardLine(Reward r) {
        boolean named = r.name() != null && !r.name().isBlank();
        if ("item".equals(r.type()) && !named) return r.count() + " x " + rewardName(r);
        return rewardName(r);
    }

    /** Height of one reward in the rewards list, including the option lines of random and choice rewards. */
    private int rewardBlock(Reward r) {
        int options = r.optionsOrEmpty().size();
        return switch (r.type()) {
            case "choice" -> CHOICE_HEAD + options * (ACTION + 2) + 4;
            case "random" -> ROW + 4 + options * OPTION_LINE;
            default -> ROW + 4;
        };
    }

    private PlayerSkin skin(UUID id) {
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    private static @Nullable UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Component coloured(String text, int argb) {
        return qt.component(Component.literal(text).withColor(argb & 0xffffff));
    }

    private static boolean within(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int scaledAlpha(int argb, int percent) {
        int alpha = (argb >>> 24) * Math.clamp(percent, 0, 100) / 100;
        return (alpha << 24) | (argb & 0xffffff);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0xffffff);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        QuestTheme t = theme();
        if (t.blur && ClientConfig.blur()) extractBlurredBackground(g);
        g.fill(0, 0, width, height, scaledAlpha(t.background, ClientConfig.backgroundOpacity()));
        drawChapterBackground(g);
    }

    private void drawChapterBackground(GuiGraphicsExtractor g) {
        Chapter c = currentChapter();
        QuestTheme t = theme();
        Identifier texture = QuestDraw.sprite(c == null ? null : c.background());
        if (texture == null) texture = t.backgroundTexture;
        int x = canvasLeft(), y = treeTop(), w = canvasRight() - x, h = height - y;
        if (texture == null || w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        drawBody(g, mouseX, mouseY);
        drawHeaderBar(g);
        drawSidebar(g, mouseX, mouseY);
        if (editing != null) editing.drawPanels(g, mouseX, mouseY);
        if (!detailsOpen()) {
            drawTitle(g);
            drawActions(g, mouseX, mouseY);
            if (!previewing()) drawFeedback(g);
        }
        if (detailsOpen()) drawDetails(g, mouseX, mouseY);
        if (previewing())
            qt.drawCentered(g, QuestText.t("draft.preview_notice"), width / 2, height - 40, theme().ready);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        QuestTheme t = theme();
        int cx = (int) centreX(), cy = (treeTop() + height) / 2;
        if (definitions() == null || ClientQuestState.snapshot == null) {
            qt.drawCentered(g, QuestText.t("loading"), cx, cy, t.muted);
        } else if (chapters().isEmpty()) {
            qt.drawCentered(g, QuestText.t(editing == null ? "no_chapters" : "editor.no_chapters"), cx, cy, t.muted);
        } else {
            drawTree(g, t, mouseX, mouseY);
        }
    }

    // ---- floating title, actions and feedback

    private void drawHeaderBar(GuiGraphicsExtractor g) {
        QuestTheme t = theme();
        int hh = headerHeight();
        if (hh <= 0) return;
        QuestDraw.panel(g, t.headerSprite, 0, 0, width, hh, t.header, 0);
        g.fill(0, hh - 1, width, hh, t.border);
    }

    private TeamInfo.@Nullable Invite invite() {
        TeamInfo team = ClientQuestState.team;
        return team == null ? null : team.invite();
    }

    private @Nullable String inviteText() {
        TeamInfo.Invite invite = invite();
        return invite == null ? null : QuestText.t("invitation", invite.from());
    }

    private void openTeam() {
        if (previewing()) return;
        Chapter c = currentChapter();
        minecraft.gui.setScreen(new TeamScreen(this, c == null ? null : c.theme()));
    }

    private String teamLabel() {
        var snapshot = ClientQuestState.snapshot;
        String name = snapshot == null ? null : snapshot.teamName();
        return name == null || name.isBlank() ? QuestText.t("team") : name;
    }

    private static String noticeText(int count) {
        return count == 1 ? QuestText.t("rewards_to_claim.one") : QuestText.t("rewards_to_claim.many", count);
    }

    private void drawTitle(GuiGraphicsExtractor g) {
        QuestTheme t = theme();
        Chapter c = currentChapter();
        if (c == null || !t.chapterTitle || t.titlePosition == Anchor.HIDDEN) return;
        Actions a = actions();
        int canvasWidth = canvasRight() - canvasLeft();
        boolean sameBand = (topBand(t.titlePosition) && topBand(t.actionsPosition))
                || (bottomBand(t.titlePosition) && bottomBand(t.actionsPosition));
        boolean centred = t.titlePosition == Anchor.TOP_CENTER || t.titlePosition == Anchor.BOTTOM_CENTER;
        int reserve = sameBand ? a.width() + GAP * 2 : 0;
        int maxWidth = Math.max(60, canvasWidth - PAD * 2 - (centred ? reserve * 2 : reserve));
        int[] stats = chapterStats(c);
        String title = qt.clip(c.title(), maxWidth);
        String count = stats[0] + "/" + stats[1];
        int barWidth = Math.min(56, Math.max(24, maxWidth / 4));
        String subtitle = c.subtitle() == null || c.subtitle().isBlank() ? "" : c.subtitle();
        int metaWidth = barWidth + 5 + qt.width(count);
        int subtitleRoom = maxWidth - metaWidth - 8;
        if (!subtitle.isEmpty() && subtitleRoom >= 40) {
            subtitle = qt.clip(subtitle, subtitleRoom);
            metaWidth += 8 + qt.width(subtitle);
        } else {
            subtitle = "";
        }
        int blockWidth = Math.max(qt.width(title), metaWidth);
        int[] origin = place(t.titlePosition, blockWidth, 23);
        int x = origin[0], y = origin[1];
        qt.draw(g, title, x, y, t.text);
        int barY = y + 15;
        g.fill(x, barY, x + barWidth, barY + 2, t.progressBack);
        if (stats[1] > 0 && stats[0] > 0) {
            g.fill(
                    x,
                    barY,
                    x + Math.max(1, barWidth * stats[0] / stats[1]),
                    barY + 2,
                    stats[0] == stats[1] ? t.complete : t.progressFill);
        }
        qt.draw(g, count, x + barWidth + 5, y + 12, t.muted);
        if (!subtitle.isEmpty()) qt.draw(g, subtitle, x + barWidth + 5 + qt.width(count) + 8, y + 12, t.subtle);
    }

    private void drawActions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        QuestTheme t = theme();
        Actions a = actions();
        String invite = inviteText();
        int textY = a.y() + (BTN - 9) / 2 + 1;
        if (invite != null && a.inviteWidth() > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, t.notification, a.inviteX(), a.y() + (BTN - 10) / 2, 10, 10);
            qt.draw(g, invite, a.inviteX() + 14, textY, t.ready);
        }
        int ready = readyQuests().size();
        if (ready > 0 && a.noticeWidth() > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, t.notification, a.noticeX(), a.y() + (BTN - 10) / 2, 10, 10);
            qt.draw(g, noticeText(ready), a.noticeX() + 14, textY, t.ready);
        }
        List<Member> members = memberList();
        for (int i = 0; i < Math.min(MAX_FACES, members.size()) && a.facesWidth() > 0; i++) {
            Member member = members.get(i);
            int fx = a.facesX() + i * (FACE + FACE_GAP), fy = a.y() + (BTN - FACE) / 2;
            g.fill(fx - 1, fy - 1, fx + FACE + 1, fy + FACE + 1, 0xff000000);
            PlayerFaceExtractor.extractRenderState(g, skin(member.id()), fx, fy, FACE);
            if (within(mouseX, mouseY, fx, fy, FACE, FACE)) {
                String name =
                        member.name() == null || member.name().isBlank() ? QuestText.t("team.unknown") : member.name();
                g.setTooltipForNextFrame(minecraft.font, qt.component(name), mouseX, mouseY);
            }
        }
    }

    /** Server feedback appears briefly at the bottom of the canvas and then fades. */
    private void drawFeedback(GuiGraphicsExtractor g) {
        String feedback = ClientQuestState.feedback;
        if (feedback == null || feedback.isEmpty()) return;
        int age = ticks - feedbackSince;
        if (age >= FEEDBACK_TICKS) return;
        int alpha = age < FEEDBACK_TICKS - FEEDBACK_FADE ? 255 : 255 * (FEEDBACK_TICKS - age) / FEEDBACK_FADE;
        if (alpha < 8) return;
        QuestTheme t = theme();
        String text = qt.clip(feedback, Math.max(60, canvasRight() - canvasLeft() - PAD * 4));
        int w = qt.width(text) + 12;
        int x = (canvasLeft() + canvasRight() - w) / 2;
        int y = height - PAD - 22 - (bottomBand(theme().controlsPosition) ? 20 : 0);
        g.fill(x, y, x + w, y + 16, withAlpha(0x000000, alpha * 150 / 255));
        qt.draw(g, text, x + 6, y + 4, withAlpha(t.text, alpha));
    }

    // ---- sidebar

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        QuestTheme t = theme();
        int sw = sidebarWidth();
        if (sw <= 0) return;
        int sx = sidebarX(), top = treeTop();
        QuestDraw.panel(g, t.sidebarSprite, sx, top, sw, height - top, t.sidebar, 0);
        int edge = sidebarOnRight() ? sx : sx + sw - 1;
        g.fill(edge, top, edge + 1, height, t.border);
        List<Chapter> chapters = chapters();
        for (int i = chapterOffset; i < Math.min(chapters.size(), chapterOffset + tabRows()); i++) {
            drawTab(g, t, chapters.get(i), tabX(), tabY(i - chapterOffset), mouseX, mouseY);
        }
    }

    private void drawTab(GuiGraphicsExtractor g, QuestTheme t, Chapter c, int x, int y, int mouseX, int mouseY) {
        boolean current = c.id().equals(chapter);
        boolean locked = editing == null && !unmetPrerequisites(c).isEmpty();
        boolean hovered = within(mouseX, mouseY, x, y, tabWidth(), tabHeight());
        int[] stats = chapterStats(c);
        int w = tabWidth(), h = tabHeight();
        if (current) {
            QuestDraw.panel(g, t.tabSelectedSprite, x, y, w, h, t.sidebarSelected, 0);
            int barX = sidebarOnRight() ? x + w - 2 : x;
            g.fill(barX, y + 3, barX + 2, y + h - 3, t.tabSelected);
        } else if (hovered && !locked) {
            g.fill(x, y, x + w, y + h, QuestDraw.mix(t.sidebar, 0xffffffff, .06f));
        } else if ((t.tab >>> 24) != 0 || t.tabSprite != null) {
            QuestDraw.panel(g, t.tabSprite, x, y, w, h, t.tab, 0);
        }
        int iconX = chaptersExpanded ? x + 7 : x + (w - ICON) / 2, iconY = y + (h - ICON) / 2;
        ItemStack stack = icon(c.icon());
        if (!stack.isEmpty()) g.item(stack, iconX, iconY);
        if (locked) g.fill(iconX, iconY, iconX + ICON, iconY + ICON, 0xaa10151c);
        boolean ready = chapterReady(c);
        if (chaptersExpanded) {
            int textX = iconX + ICON + 7;
            int right = x + w - 8 - (ready ? 8 : 0);
            qt.drawClipped(
                    g,
                    c.title(),
                    textX,
                    y + (h - 9) / 2 + (t.sidebarProgress && stats[1] > 0 && !locked ? -2 : 1),
                    right - textX,
                    locked ? t.subtle : current ? t.text : t.muted);
        }
        if (ready && !locked) {
            int dotX = chaptersExpanded ? x + w - 10 : x + w - 7, dotY = chaptersExpanded ? y + (h - 5) / 2 : y + 3;
            QuestDraw.geometry(g, "circle", dotX, dotY, 5, 5, t.ready);
        }
        if (t.sidebarProgress && stats[1] > 0 && !locked) {
            int barX = chaptersExpanded ? iconX + ICON + 7 : x + 4, barRight = chaptersExpanded ? x + w - 8 : x + w - 4;
            int barY = y + h - 5;
            if (barRight > barX) {
                g.fill(barX, barY, barRight, barY + 2, t.progressBack);
                if (stats[0] > 0)
                    g.fill(
                            barX,
                            barY,
                            barX + Math.max(1, (barRight - barX) * stats[0] / stats[1]),
                            barY + 2,
                            stats[0] == stats[1] ? t.complete : t.progressFill);
            }
        }
        if (hovered) {
            g.setTooltipForNextFrame(font, chapterTooltip(t, c), Optional.empty(), mouseX, mouseY, t.tooltipStyle);
        }
    }

    private List<Component> chapterTooltip(QuestTheme t, Chapter c) {
        List<Component> lines = new ArrayList<>();
        lines.add(coloured(c.title(), t.text));
        List<String> unmet = unmetPrerequisites(c);
        if (!unmet.isEmpty()) {
            lines.add(coloured(QuestText.t("chapter.locked"), t.locked));
            lines.add(coloured(QuestText.t("chapter.locked_hint", String.join(" / ", unmet)), t.subtle));
        } else {
            int[] stats = chapterStats(c);
            lines.add(coloured(QuestText.t("chapter.progress", stats[0], stats[1]), t.muted));
            if (chapterReady(c)) lines.add(coloured(QuestText.t("chapter.ready"), t.ready));
        }
        if (c.subtitle() != null && !c.subtitle().isBlank()) lines.add(coloured(c.subtitle(), t.subtle));
        return lines;
    }

    // ---- tree

    private void drawTree(GuiGraphicsExtractor g, QuestTheme t, int mouseX, int mouseY) {
        List<Quest> quests = visibleQuests();
        Map<String, Quest> byId = new HashMap<>();
        for (Quest q : quests) byId.put(q.id(), q);
        Quest hovered = detailsOpen() ? null : nodeAt(t, quests, mouseX, mouseY);
        g.enableScissor(canvasLeft(), treeTop(), canvasRight(), height);
        if (editing != null) editing.drawGrid(g, t);
        else if (t.canvasGrid) drawGrid(g, t);
        drawConnections(g, t, quests, byId);
        for (Quest q : quests) drawNode(g, t, q, q == hovered);
        if (editing != null) editing.drawCanvas(g, t, mouseX, mouseY);
        g.disableScissor();
        if (hovered != null && (editing == null || editing.hoverAllowed())) {
            List<Component> lines =
                    editing == null ? nodeTooltip(t, hovered, view(hovered)) : editing.tooltip(t, hovered);
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY, t.tooltipStyle);
        }
    }

    /** A dot at every grid crossing: reads as depth and movement without competing with the connections. */
    private void drawGrid(GuiGraphicsExtractor g, QuestTheme t) {
        int spacing = Math.max(14, (int) Math.round(28 * canvas.zoom));
        int offsetX = Math.floorMod((int) Math.round(canvas.panX), spacing);
        int offsetY = Math.floorMod((int) Math.round(canvas.panY), spacing);
        int dot = QuestDraw.mix(t.border, t.background | 0xff000000, .45f);
        for (int x = canvasLeft() + Math.floorMod(offsetX - canvasLeft(), spacing); x < canvasRight(); x += spacing) {
            for (int y = treeTop() + Math.floorMod(offsetY - treeTop(), spacing); y < height; y += spacing) {
                g.fill(x, y, x + 1, y + 1, dot);
            }
        }
    }

    /** Optional outline pass first, then the colour pass, so no line is painted over another line's colour. */
    private void drawConnections(GuiGraphicsExtractor g, QuestTheme t, List<Quest> quests, Map<String, Quest> byId) {
        int lineWidth = Math.max(1, (int) Math.round(t.lineWidth * Math.min(1.0, canvas.zoom * 1.2)));
        for (int pass = t.lineOutlines ? 0 : 1; pass < 2; pass++) {
            boolean outline = pass == 0;
            for (Quest child : quests) {
                for (String id : QuestViews.list(child.prerequisites())) {
                    Quest parent = byId.get(id);
                    if (parent == null) continue;
                    int colour = outline ? t.lineOutline : connectionColour(t, parent, child);
                    int px = nodeCentreX(parent), py = nodeCentreY(parent);
                    int cx = nodeCentreX(child), cy = nodeCentreY(child);
                    if (!t.directConnections) {
                        QuestDraw.elbow(g, px, py, cx, cy, lineWidth, outline ? 1 : 0, colour);
                        continue;
                    }
                    double dx = cx - px, dy = cy - py;
                    double length = Math.hypot(dx, dy);
                    if (length < 1) continue;
                    // Start and end at the node edges, so the line never shows through a translucent node.
                    boolean arrows = t.arrowheads || editing != null;
                    double startTrim = nodeSize(t, parent) / 2.0, endTrim = nodeSize(t, child) / 2.0 + (arrows ? 2 : 0);
                    if (startTrim + endTrim >= length) continue;
                    double ux = dx / length, uy = dy / length;
                    double ax = px + ux * startTrim,
                            ay = py + uy * startTrim,
                            bx = cx - ux * endTrim,
                            by = cy - uy * endTrim;
                    drawLine(g, ax, ay, bx, by, lineWidth, outline ? 1 : 0, colour);
                    if (arrows && !outline) QuestDraw.arrowhead(g, bx, by, ux, uy, Math.max(3, lineWidth + 3), colour);
                }
            }
        }
    }

    /** Draws a straight segment, clipped to the canvas first so very long off-screen edges stay cheap. */
    private void drawLine(
            GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int width, int pad, int colour) {
        double dx = x1 - x0, dy = y1 - y0;
        double start = 0, end = 1;
        double[] origins = {x0, y0}, deltas = {dx, dy};
        double[] low = {canvasLeft() - 8, treeTop() - 8}, high = {canvasRight() + 8, height + 8};
        for (int axis = 0; axis < 2; axis++) {
            if (deltas[axis] == 0) {
                if (origins[axis] < low[axis] || origins[axis] > high[axis]) return;
            } else {
                double a = (low[axis] - origins[axis]) / deltas[axis];
                double b = (high[axis] - origins[axis]) / deltas[axis];
                start = Math.max(start, Math.min(a, b));
                end = Math.min(end, Math.max(a, b));
            }
        }
        if (start > end) return;
        QuestDraw.direct(
                g,
                (int) (x0 + dx * start),
                (int) (y0 + dy * start),
                (int) (x0 + dx * end),
                (int) (y0 + dy * end),
                width,
                pad,
                colour);
    }

    private int connectionColour(QuestTheme t, Quest parent, Quest child) {
        if (editing != null) return t.lineAvailable;
        if (view(child).completed()) return t.lineComplete;
        if (view(parent).completed()) return t.lineAvailable;
        return t.lineLocked;
    }

    private void drawNode(GuiGraphicsExtractor g, QuestTheme t, Quest q, boolean hovered) {
        int s = nodeSize(t, q);
        int cx = nodeCentreX(q), cy = nodeCentreY(q);
        if (cx + s < canvasLeft() || cx - s > canvasRight() || cy + s < treeTop() || cy - s > height) return;
        QuestView view = view(q);
        // Progress is meaningless while editing, so the editor shows every quest as an ordinary, open node.
        boolean locked = editing == null && QuestViews.locked(view);
        boolean ready = editing == null && view.completed() && !view.claimed();
        int x = cx - s / 2, y = cy - s / 2;
        QuestTheme.Shape shape = t.shape(q.shapeOrDefault());
        if (t.flatNodes || shape.complete() == null) drawFlatFrame(g, t, q, view, shape, x, y, s, hovered);
        else drawFrame(g, t, q, view, shape, x, y, s, locked);
        int iconSize = Math.max(4, Math.round(s * t.nodeIconScale));
        int ix = cx - iconSize / 2, iy = cy - iconSize / 2;
        drawQuestIcon(g, q, ix, iy, iconSize);
        if (locked) g.fill(ix, iy, ix + iconSize, iy + iconSize, 0xaa10151c);
        if (editing != null) editing.decorate(g, t, q, shape, x, y, s);
        if (editing == null && s >= 20) {
            int bx = x + s - 7, by = y - 3;
            int bob = t.animations ? (ticks / 10) % 2 : 0;
            if (ready) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, t.notification, bx - 1, by - 1 + bob, 10, 10);
            } else if (view.completed()) {
                drawBadge(g, t, t.badgeComplete, bx, by, true);
            } else if (locked) {
                drawBadge(g, t, t.badgeLocked, bx, by, false);
            }
        }
        int labelY = y + s + 3;
        if (editing == null
                && t.nodeProgressBars
                && view.available()
                && !view.completed()
                && QuestViews.progress(q, view) > 0) {
            drawNodeProgress(g, t, q, view, cx, labelY, s);
            labelY += 6;
        }
        if (t.nodeTitles && canvas.zoom >= .6) {
            String title = qt.clip(q.title(), 110);
            qt.draw(g, title, cx - qt.width(title) / 2, labelY, locked ? t.subtle : t.muted);
        }
    }

    /** Flat node: status-coloured edge, dark face, in the shape's geometry. A soft halo marks selection and claimable rewards. */
    private void drawFlatFrame(
            GuiGraphicsExtractor g,
            QuestTheme t,
            Quest q,
            QuestView view,
            QuestTheme.Shape shape,
            int x,
            int y,
            int s,
            boolean hovered) {
        boolean locked = editing == null && QuestViews.locked(view);
        boolean ready = editing == null && view.completed() && !view.claimed();
        String geometry = shape.geometry();
        boolean done = editing == null && view.completed();
        int edge = done ? (ready ? t.ready : t.complete) : locked ? t.lineLocked : t.accent;
        int face = done
                ? QuestDraw.mix(t.nodeFace, edge, .18f)
                : locked ? t.nodeFaceLocked : QuestDraw.mix(t.nodeFace, t.accent, .08f);
        if (hovered && !locked) face = QuestDraw.mix(face, 0xffffffff, .08f);
        if (hovered) edge = QuestDraw.mix(edge, 0xffffffff, .4f);
        boolean isSelected = q.id().equals(selected) || (editing != null && editing.isSelected(q));
        if (isSelected) {
            QuestDraw.geometry(g, geometry, x - 4, y - 4, s + 8, s + 8, withAlpha(t.selection, 0x40));
            edge = t.selection;
        } else if (ready) {
            int pulse = t.animations ? 0x26 + (int) (0x1a * Math.sin(ticks / 6.0)) : 0x30;
            QuestDraw.geometry(g, geometry, x - 4, y - 4, s + 8, s + 8, withAlpha(t.ready, pulse));
        }
        int border = s >= 24 ? t.nodeBorder : 1;
        QuestDraw.framed(g, geometry, x, y, s, s, border, edge, face);
    }

    private void drawBadge(
            GuiGraphicsExtractor g, QuestTheme t, @Nullable Identifier sprite, int x, int y, boolean complete) {
        if (sprite != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - 1, y - 1, 10, 10);
        } else if (complete) {
            drawCheckBadge(g, t, x, y);
        } else {
            drawLockBadge(g, t, x, y);
        }
    }

    private void drawCheckBadge(GuiGraphicsExtractor g, QuestTheme t, int x, int y) {
        QuestDraw.geometry(g, "circle", x - 1, y - 1, 12, 12, 0xff0b0f14);
        QuestDraw.geometry(g, "circle", x, y, 10, 10, t.complete);
        int ink = 0xff0b0f14;
        int[][] pixels = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}
        };
        for (int[] pixel : pixels) g.fill(x + pixel[0] - 1, y + pixel[1] - 1, x + pixel[0], y + pixel[1], ink);
    }

    private void drawLockBadge(GuiGraphicsExtractor g, QuestTheme t, int x, int y) {
        QuestDraw.geometry(g, "circle", x - 1, y - 1, 12, 12, 0xff0b0f14);
        QuestDraw.geometry(g, "circle", x, y, 10, 10, t.lineLocked);
        int ink = t.muted | 0xff000000;
        g.fill(x + 3, y + 1, x + 7, y + 2, ink);
        g.fill(x + 3, y + 2, x + 4, y + 5, ink);
        g.fill(x + 6, y + 2, x + 7, y + 5, ink);
        g.fill(x + 2, y + 4, x + 8, y + 9, ink);
    }

    private void drawFrame(
            GuiGraphicsExtractor g,
            QuestTheme t,
            Quest q,
            QuestView view,
            QuestTheme.Shape shape,
            int x,
            int y,
            int s,
            boolean locked) {
        Identifier frame = editing == null && view.completed() ? shape.complete() : shape.incomplete();
        if (frame != null) {
            if (locked) g.blitSprite(RenderPipelines.GUI_TEXTURED, frame, x, y, s, s, t.lockedAlpha);
            else g.blitSprite(RenderPipelines.GUI_TEXTURED, frame, x, y, s, s);
        }
        if (q.id().equals(selected) || (editing != null && editing.isSelected(q)))
            g.outline(x - 3, y - 3, s + 6, s + 6, t.selection);
    }

    /** Thin bar under an available node showing how far along it is. */
    private void drawNodeProgress(GuiGraphicsExtractor g, QuestTheme t, Quest q, QuestView view, int cx, int y, int s) {
        double fraction = QuestViews.progress(q, view);
        int barWidth = Math.max(8, s - 6);
        int bx = cx - barWidth / 2;
        g.fill(bx, y, bx + barWidth, y + 2, t.progressBack);
        g.fill(bx, y, bx + Math.max(1, (int) Math.round(barWidth * fraction)), y + 2, t.progressFill);
    }

    @Nullable
    Quest nodeAt(QuestTheme t, List<Quest> quests, int mouseX, int mouseY) {
        if (mouseX < canvasLeft() || mouseX >= canvasRight() || mouseY < treeTop() || mouseY >= height) return null;
        Quest hit = null;
        for (Quest q : quests) {
            int s = nodeSize(t, q);
            int x = nodeCentreX(q) - s / 2, y = nodeCentreY(q) - s / 2;
            if (within(mouseX, mouseY, x, y, s, s)) hit = q;
        }
        return hit;
    }

    private List<Component> nodeTooltip(QuestTheme t, Quest q, QuestView view) {
        List<Component> lines = new ArrayList<>();
        lines.add(coloured(q.title(), t.text));
        lines.add(coloured(QuestViews.status(view), QuestViews.statusColor(t, view)));
        List<Objective> objectives = QuestViews.list(q.objectives());
        if (!view.completed() && !objectives.isEmpty()) {
            Objective first = objectives.getFirst();
            String progress = QuestViews.objectiveLabel(first) + ": " + QuestViews.done(view, first) + "/"
                    + Math.max(1, first.count());
            lines.add(coloured(progress, t.muted));
        }
        if (QuestViews.locked(view)) {
            List<String> titles = QuestViews.list(q.prerequisites()).stream()
                    .map(this::titleOf)
                    .toList();
            lines.add(coloured(QuestText.t("node.requires", String.join(" / ", titles)), t.subtle));
        }
        lines.add(coloured(QuestText.t("node.open"), t.subtle));
        return lines;
    }

    private String titleOf(String questId) {
        Quest q = findQuest(questId);
        return q == null ? questId : q.title();
    }

    // ---- details window

    /** Lays out the details content top-down; both drawing and widget placement use this so they always agree. */
    private DetailLayout layoutDetails(Quest q, QuestView view) {
        int w = textWidth();
        int titleLines =
                Math.max(1, qt.wrap(q.title(), w - HEAD_TEXT - navReserve()).size());
        boolean subtitle = q.subtitle() != null && !q.subtitle().isBlank();
        int statusY = Math.max(HEAD_ICON, titleLines * 10 + (subtitle ? 11 : 1)) + 6;
        int y = statusY + 9 + 10;
        int descriptionY = y;
        int descriptionLines = QuestViews.text(q.description()).isBlank()
                ? 0
                : qt.wrap(QuestViews.text(q.description()), w).size();
        y += descriptionLines == 0 ? 0 : descriptionLines * 10 + 10;
        int objectivesY = y;
        y += SECTION;
        List<Objective> objectives = QuestViews.list(q.objectives());
        int[] objectiveY = new int[objectives.size()];
        for (int i = 0; i < objectives.size(); i++) {
            objectiveY[i] = y;
            y += QuestViews.actionable(objectives.get(i)) ? ROW + 2 + ACTION + 6 : ROW + 4;
        }
        int contributionsY = y;
        if (!view.contributions().isEmpty()) y += 16;
        if (QuestViews.locked(view)) y += 4;
        int requiresY = y;
        List<String> prerequisites = QuestViews.list(q.prerequisites());
        int[] prerequisiteY = new int[prerequisites.size()];
        if (QuestViews.locked(view)) {
            y += SECTION;
            for (int i = 0; i < prerequisites.size(); i++) {
                prerequisiteY[i] = y;
                y += 12;
            }
            y += 6;
        }
        int tasksBottom = y;
        if (detailColumns()) y = objectivesY;
        int rewardsY = y;
        y += SECTION;
        List<Reward> rewards = QuestViews.list(q.rewards());
        int[] rewardY = new int[rewards.size()];
        for (int i = 0; i < rewards.size(); i++) {
            rewardY[i] = y;
            y += rewardBlock(rewards.get(i));
        }
        int claimY = y + 2;
        y = claimY + 20;
        y = Math.max(y, tasksBottom);
        int pinY = y + 4;
        y = pinY + 18;
        return new DetailLayout(
                statusY,
                descriptionY,
                objectivesY,
                objectiveY,
                contributionsY,
                requiresY,
                prerequisiteY,
                rewardsY,
                rewardY,
                claimY,
                pinY,
                y);
    }

    /** Room kept free at the right of the title for the previous, next and close buttons. */
    private static int navReserve() {
        return NAV * 3 + NAV_GAP * 2 + 8;
    }

    private void drawDetails(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Quest q = current();
        if (q == null) return;
        QuestTheme t = theme();
        int px = panelX();
        g.fill(0, 0, width, height, t.overlay);
        g.fill(px - 1, panelTop() + 2, panelRight() + 1, panelBottom() + 3, 0x55000000);
        QuestDraw.panel(
                g,
                t.panelSprite,
                px,
                panelTop(),
                panelWidth(),
                panelBottom() - panelTop(),
                t.panel | 0xff000000,
                t.border);
        QuestView view = view(q);
        DetailLayout layout = layoutDetails(q, view);
        g.enableScissor(px + 1, detailViewTop(), panelRight() - 1, detailViewBottom() - 1);
        drawDetailHeader(g, t, q, view, layout);
        drawObjectives(g, t, q, view, layout, mouseX, mouseY);
        if (!view.contributions().isEmpty()) drawContributions(g, t, view, contentY(layout.contributionsY()));
        if (QuestViews.locked(view)) drawPrerequisites(g, t, q, layout, mouseX, mouseY);
        drawRewards(g, t, q, layout, mouseX, mouseY);
        g.disableScissor();
        drawDetailScrollbar(g, t, layout);
    }

    private void drawDetailHeader(GuiGraphicsExtractor g, QuestTheme t, Quest q, QuestView view, DetailLayout layout) {
        int x = textX(), base = contentBase();
        boolean locked = QuestViews.locked(view);
        int accent = view.completed() ? (view.claimed() ? t.complete : t.ready) : locked ? t.lineLocked : t.accent;
        QuestTheme.Shape shape = t.shape(q.shapeOrDefault());
        QuestDraw.framed(
                g, shape.geometry(), x, base, HEAD_ICON, HEAD_ICON, 2, accent, locked ? t.nodeFaceLocked : t.nodeFace);
        drawQuestIcon(g, q, x + 6, base + 6, 12);
        if (locked) g.fill(x + 6, base + 6, x + 18, base + 18, 0xaa10151c);
        int y = base;
        for (var line : qt.wrap(q.title(), textWidth() - HEAD_TEXT - navReserve())) {
            qt.drawLine(g, line, x + HEAD_TEXT, y, t.text);
            y += 10;
        }
        if (q.subtitle() != null && !q.subtitle().isBlank()) {
            qt.drawClipped(g, q.subtitle(), x + HEAD_TEXT, y + 1, textWidth() - HEAD_TEXT - navReserve(), t.subtle);
        }
        int statusColour = QuestViews.statusColor(t, view);
        int statusY = contentY(layout.statusY());
        QuestDraw.geometry(g, "circle", x, statusY + 2, 5, 5, statusColour);
        qt.draw(g, QuestViews.status(view), x + 9, statusY, statusColour);
        if (q.repeatable())
            qt.draw(g, QuestText.t("repeatable"), x + 9 + qt.width(QuestViews.status(view)) + 8, statusY, t.subtle);
        y = contentY(layout.descriptionY());
        for (var line : qt.wrap(QuestViews.text(q.description()), textWidth())) {
            qt.drawLine(g, line, x, y, t.muted);
            y += 10;
        }
    }

    private void drawSectionHeading(
            GuiGraphicsExtractor g, QuestTheme t, String heading, String note, int x, int y, int width) {
        qt.draw(g, heading, x, y, t.heading);
        int noteX = x + qt.width(heading) + 6;
        if (noteX + qt.width(note) <= x + width) qt.draw(g, note, noteX, y, t.subtle);
    }

    private void drawObjectives(
            GuiGraphicsExtractor g,
            QuestTheme t,
            Quest q,
            QuestView view,
            DetailLayout layout,
            int mouseX,
            int mouseY) {
        List<Objective> objectives = QuestViews.list(q.objectives());
        drawSectionHeading(
                g,
                t,
                QuestText.t("details.tasks"),
                QuestText.t("details.tasks_note"),
                textX(),
                contentY(layout.objectivesY()),
                taskWidth());
        for (int i = 0; i < objectives.size(); i++) {
            drawObjective(g, t, q, view, objectives.get(i), textX(), contentY(layout.objectiveY()[i]), mouseX, mouseY);
        }
    }

    private void drawObjective(
            GuiGraphicsExtractor g,
            QuestTheme t,
            Quest q,
            QuestView view,
            Objective o,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        int w = taskWidth();
        int done = QuestViews.done(view, o), need = Math.max(1, o.count());
        boolean finished = done >= need;
        drawObjectiveIcon(g, q, o, x, y + 3);
        String count = done + "/" + need;
        qt.drawRight(g, count, x + w, y + 3, finished ? t.complete : t.muted);
        int labelRight = x + w - qt.width(count) - 8;
        qt.drawClipped(
                g, QuestViews.objectiveLabel(o), x + 22, y + 3, labelRight - (x + 22), finished ? t.muted : t.text);
        if (need > 1) {
            int barY = y + 15;
            g.fill(x + 22, barY, x + w, barY + 2, t.progressBack);
            int filled = (w - 22) * done / need;
            if (filled > 0) g.fill(x + 22, barY, x + 22 + filled, barY + 2, finished ? t.complete : t.progressFill);
        }
        if (within(mouseX, mouseY, x, y, w, ROW) && mouseY >= detailViewTop() && mouseY < detailViewBottom()) {
            g.setTooltipForNextFrame(
                    font, qt.component(QuestViews.objectiveLabel(o) + " - " + done + "/" + o.count()), mouseX, mouseY);
        }
    }

    private void drawContributions(GuiGraphicsExtractor g, QuestTheme t, QuestView view, int y) {
        int x = textX();
        for (var entry : view.contributions().entrySet()) {
            if (x + 32 > textX() + taskWidth()) break;
            UUID id = parseUuid(entry.getKey());
            if (id != null) PlayerFaceExtractor.extractRenderState(g, skin(id), x, y, 8);
            String amount = String.valueOf(entry.getValue());
            qt.draw(g, amount, x + 10, y, t.muted);
            x += 10 + qt.width(amount) + 12;
        }
    }

    private void drawPrerequisites(
            GuiGraphicsExtractor g, QuestTheme t, Quest q, DetailLayout layout, int mouseX, int mouseY) {
        int x = textX();
        drawSectionHeading(
                g,
                t,
                QuestText.t("details.requires"),
                QuestText.t("details.requires_note"),
                x,
                contentY(layout.requiresY()),
                taskWidth());
        List<String> prerequisites = QuestViews.list(q.prerequisites());
        for (int i = 0; i < prerequisites.size(); i++) {
            int y = contentY(layout.prerequisiteY()[i]);
            String label = prerequisiteLabel(prerequisites.get(i));
            int labelWidth = qt.width(label);
            boolean hovered = within(mouseX, mouseY, x, y, labelWidth, 10);
            qt.draw(g, label, x, y, hovered ? t.heading : t.text);
            if (hovered) g.fill(x, y + 9, x + labelWidth, y + 10, t.heading);
        }
    }

    private String prerequisiteLabel(String questId) {
        return qt.clip(titleOf(questId), taskWidth());
    }

    private void drawRewards(
            GuiGraphicsExtractor g, QuestTheme t, Quest q, DetailLayout layout, int mouseX, int mouseY) {
        int x = rewardX(), w = taskWidth();
        drawSectionHeading(
                g,
                t,
                QuestText.t("details.rewards"),
                QuestText.t("details.rewards_note"),
                x,
                contentY(layout.rewardsY()),
                w);
        List<Reward> rewards = QuestViews.list(q.rewards());
        for (int i = 0; i < rewards.size(); i++) {
            Reward r = rewards.get(i);
            int y = contentY(layout.rewardY()[i]);
            if ("choice".equals(r.type())) {
                qt.drawClipped(g, rewardName(r), x, y + 3, w, t.heading);
                List<Reward> options = r.optionsOrEmpty();
                for (int k = 0; k < options.size(); k++) {
                    ItemStack optionStack = rewardStack(options.get(k));
                    if (optionStack.isEmpty()) continue;
                    int optionY = y + CHOICE_HEAD + k * (ACTION + 2);
                    g.item(optionStack, x, optionY + 1);
                }
                continue;
            }
            ItemStack stack = rewardStack(r);
            if (!stack.isEmpty()) {
                g.item(stack, x, y + 3);
                g.itemDecorations(font, stack, x, y + 3);
            }
            qt.drawClipped(g, rewardName(r), x + 22, y + 7, w - 22, t.text);
            if ("random".equals(r.type())) {
                List<Reward> options = r.optionsOrEmpty();
                int total = options.stream().mapToInt(Reward::weightOrDefault).sum();
                for (int k = 0; k < options.size(); k++) {
                    int percent = Math.max(1, Math.round(100f * options.get(k).weightOrDefault() / Math.max(1, total)));
                    qt.drawClipped(
                            g,
                            QuestText.t("reward.chance", percent, rewardLine(options.get(k))),
                            x + 22,
                            y + ROW + 2 + k * OPTION_LINE,
                            w - 22,
                            t.muted);
                }
            }
            if (within(mouseX, mouseY, x, y, w, ROW) && mouseY >= detailViewTop() && mouseY < detailViewBottom()) {
                g.setTooltipForNextFrame(font, qt.component(rewardLine(r)), mouseX, mouseY);
            }
        }
    }

    private void drawDetailScrollbar(GuiGraphicsExtractor g, QuestTheme t, DetailLayout layout) {
        int viewHeight = detailViewBottom() - detailViewTop();
        int content = layout.height() + CONTENT_PAD * 2;
        if (content <= viewHeight) return;
        int trackX = panelRight() - 4;
        g.fill(trackX, detailViewTop() + 2, trackX + 2, detailViewBottom() - 2, t.progressBack);
        int thumb = Math.max(12, viewHeight * viewHeight / content);
        int thumbY = detailViewTop() + 2 + detailScroll * (viewHeight - 4 - thumb) / Math.max(1, content - viewHeight);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, t.subtle);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        if (editing != null && editing.mouseClicked(e)) return true;
        if (e.button() != 0) return false;
        double x = e.x(), y = e.y();
        if (detailsOpen()) {
            if (within((int) x, (int) y, panelX(), panelTop(), panelWidth(), panelBottom() - panelTop()))
                return clickDetails(x, y);
            closeDetails();
            return true;
        }
        if (sidebarShown() && x >= sidebarX() && x < sidebarX() + sidebarWidth()) return clickSidebar(x, y);
        if (clickActions(x, y)) return true;
        if (x >= canvasLeft() && x < canvasRight() && y >= treeTop() && y < height) return clickTree(x, y);
        return false;
    }

    /** The invitation and reward notices are text, not buttons. */
    private boolean clickActions(double x, double y) {
        Actions a = actions();
        int top = a.y() + (BTN - 10) / 2;
        if (y < top || y >= top + 10) return false;
        if (a.inviteWidth() > 0 && x >= a.inviteX() && x < a.inviteX() + a.inviteWidth()) {
            openTeam();
            return true;
        }
        if (a.noticeWidth() > 0 && x >= a.noticeX() && x < a.noticeX() + a.noticeWidth()) {
            List<Quest> ready = readyQuests();
            if (ready.isEmpty()) return false;
            openQuest(ready.getFirst());
            return true;
        }
        return false;
    }

    private boolean clickSidebar(double x, double y) {
        if (x < tabX() || x >= tabX() + tabWidth()) return true;
        int rel = (int) y - tabY(0);
        if (rel < 0 || rel % tabPitch() >= tabHeight()) return true;
        int index = chapterOffset + rel / tabPitch();
        List<Chapter> chapters = chapters();
        if (index >= chapters.size()) return true;
        Chapter c = chapters.get(index);
        if (editing != null || unmetPrerequisites(c).isEmpty()) selectChapter(c);
        return true;
    }

    private boolean clickTree(double x, double y) {
        Quest q = nodeAt(theme(), visibleQuests(), (int) x, (int) y);
        if (q != null) {
            focusQuest(q.id());
            return true;
        }
        dragging = true;
        return true;
    }

    private boolean clickDetails(double x, double y) {
        Quest q = current();
        if (q == null) return true;
        QuestView view = view(q);
        if (!QuestViews.locked(view)) return true;
        DetailLayout layout = layoutDetails(q, view);
        List<String> prerequisites = QuestViews.list(q.prerequisites());
        for (int i = 0; i < prerequisites.size(); i++) {
            int rowY = contentY(layout.prerequisiteY()[i]);
            int labelWidth = qt.width(prerequisiteLabel(prerequisites.get(i)));
            boolean hit = x >= textX() && x < textX() + labelWidth && y >= rowY && y < rowY + 10;
            Quest prerequisite = findQuest(prerequisites.get(i));
            if (hit && prerequisite != null) {
                openQuest(prerequisite);
                break;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (editing != null && editing.mouseDragged(e)) return true;
        if (dragging) {
            canvas.panX += dx;
            canvas.panY += dy;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (editing != null) editing.mouseReleased(e);
        dragging = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (editing != null && editing.mouseScrolled(x, y, scrollY)) return true;
        if (detailsOpen()) {
            scrollDetails((int) Math.round(-scrollY * 24));
            return true;
        }
        if (x >= canvasLeft() && x < canvasRight()) {
            canvas.zoomAt(x, y, Math.pow(1.15, scrollY));
            return true;
        }
        if (sidebarShown() && x >= sidebarX() && x < sidebarX() + sidebarWidth()) {
            chapterOffset -= (int) Math.signum(scrollY);
            clampChapterOffset();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.hasControlDownWithQuirk() && e.key() == GLFW.GLFW_KEY_F) {
            minecraft.gui.setScreen(new QuestSearchScreen(this));
            return true;
        }
        if (editing != null && editing.keyPressed(e)) return true;
        if (e.isEscape() && selected != null) {
            closeDetails();
            return true;
        }
        if (detailsOpen() && (e.key() == GLFW.GLFW_KEY_LEFT || e.key() == GLFW.GLFW_KEY_RIGHT)) {
            cycleQuest(e.key() == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_F && !detailsOpen()) {
            fit();
            return true;
        }
        return super.keyPressed(e);
    }
}
