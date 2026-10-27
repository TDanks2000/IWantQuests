package dev.iwantquests.client;

import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.client.theme.ThemeManager;
import dev.iwantquests.client.ui.QuestButton;
import dev.iwantquests.client.ui.QuestDraw;
import dev.iwantquests.client.ui.QuestText;
import dev.iwantquests.network.PlayerSnapshot.Member;
import dev.iwantquests.network.TeamInfo;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Team management: members, invitations, invites (owners), team creation (solo) and leaving.
 * Styled like the quest book; all state comes from {@link ClientQuestState#team}. Escape returns to the parent screen.
 */
public final class TeamScreen extends Screen {
    private static final int PAD = 8, MARGIN = 12, MAX_PANEL = 300, TOP_PAD = 8;
    private static final int HEADER = 34, FOOTER = 40, FOOTER_SOLO = 16, GAP = 10;
    private static final int FACE = 12, FACE_GAP = 4, ROW = 20, HEADING = 14;
    private static final int BACK_WIDTH = 44, REFRESH_TICKS = 100, SCROLL_STEP = 12, NAME_MAX = 32;

    /** Vertical offsets of the scrollable sections, relative to the top of the content (before scrolling). */
    private record Sections(int createY, int membersY, int invitesY, int height) {}

    private final @Nullable Screen parent;
    private final QuestText qt = new QuestText(this::theme);
    private @Nullable EditBox nameBox, searchBox;
    private @Nullable QuestButton createButton;
    private String appliedFilter = "";
    private int scroll, ticks, seenRevision = -1;
    private @Nullable TeamInfo seenTeam;
    private long inviteSeenAt;

    private final @Nullable String chapterTheme;

    public TeamScreen(@Nullable Screen parent) {
        this(parent, null);
    }

    /** {@code chapterTheme} is the theme of the chapter the player came from, so both screens match. */
    public TeamScreen(@Nullable Screen parent, @Nullable String chapterTheme) {
        super(Component.translatable("iwantquests.ui.team"));
        this.parent = parent;
        this.chapterTheme = chapterTheme;
        // Feedback from earlier quest actions does not belong on this screen.
        ClientQuestState.feedback = "";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        seenRevision = ClientQuestState.revision;
        requestTeam();
        widgets();
    }

    @Override
    public void tick() {
        if (++ticks % REFRESH_TICKS == 0) requestTeam();
        if (ClientQuestState.revision != seenRevision || filterChanged()) {
            seenRevision = ClientQuestState.revision;
            widgets();
        }
        if (createButton != null && nameBox != null)
            createButton.active = !nameBox.getValue().isBlank();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static void requestTeam() {
        ClientQuestState.action("team_info", "", "");
    }

    private boolean filterChanged() {
        return searchBox != null && !searchBox.getValue().equals(appliedFilter);
    }

    // ---------------------------------------------------------------- data helpers

    private QuestTheme theme() {
        return ThemeManager.get(chapterTheme);
    }

    private static boolean canInvite(TeamInfo team) {
        return team.isOwner() && !team.solo();
    }

    private static boolean canKick(TeamInfo team, Member member) {
        return canInvite(team) && !member.id().equals(team.owner());
    }

    private static String displayName(Member member) {
        return member.name() == null || member.name().isBlank() ? QuestText.t("team.unknown") : member.name();
    }

    private static String teamTitle(TeamInfo team) {
        return team.name() == null || team.name().isBlank() ? QuestText.t("team") : team.name();
    }

    /** Online players outside the team, filtered by the applied search text. */
    private List<Member> invitable(TeamInfo team) {
        String filter = appliedFilter.strip().toLowerCase(Locale.ROOT);
        return team.invitable().stream()
                .filter(m -> displayName(m).toLowerCase(Locale.ROOT).contains(filter))
                .toList();
    }

    private PlayerSkin skin(UUID id) {
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    private static int scaledAlpha(int argb, int percent) {
        int alpha = (argb >>> 24) * Math.clamp(percent, 0, 100) / 100;
        return (alpha << 24) | (argb & 0xffffff);
    }

    // ---------------------------------------------------------------- layout

    private int panelWidth() {
        return Math.min(MAX_PANEL, width - PAD * 2);
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelRight() {
        return panelX() + panelWidth();
    }

    private int innerLeft() {
        return panelX() + MARGIN;
    }
    /** Leaves a 4px gutter on the right for the scrollbar. */
    private int innerWidth() {
        return panelWidth() - MARGIN * 2 - 4;
    }

    private int rowRight() {
        return innerLeft() + innerWidth();
    }

    private int backX() {
        return panelRight() - MARGIN - BACK_WIDTH;
    }

    private int viewTop() {
        return PAD + HEADER;
    }

    private int footerHeight() {
        return ClientQuestState.team != null && !ClientQuestState.team.solo() ? FOOTER : FOOTER_SOLO;
    }

    private int viewBottom() {
        return height - PAD - footerHeight();
    }

    private int contentY(int relative) {
        return viewTop() + TOP_PAD + relative - scroll;
    }

    private boolean fits(int y, int h) {
        return y >= viewTop() && y + h <= viewBottom();
    }

    private int maxScroll(int contentHeight) {
        return Math.max(0, contentHeight + TOP_PAD - (viewBottom() - viewTop()));
    }

    private int removeX() {
        return rowRight() - 56;
    }

    private int explainLines() {
        return qt.wrap(QuestText.t("team.explain"), innerWidth()).size();
    }

    private static String bannerText(TeamInfo.Invite invite) {
        String team = invite.teamName() == null ? QuestText.t("team.banner_unnamed") : invite.teamName();
        return QuestText.t("team.banner", invite.from(), team);
    }

    private int bannerLines(TeamInfo.Invite invite) {
        return qt.wrap(bannerText(invite), innerWidth() - 12).size();
    }

    private Sections sections(TeamInfo team) {
        int y = 0, createY = -1, invitesY = -1;
        if (team.invite() != null) y += bannerLines(team.invite()) * 10 + 44 + GAP;
        if (team.solo()) {
            createY = y;
            y += explainLines() * 10 + 34 + GAP;
        }
        int membersY = y;
        y += HEADING + team.members().size() * ROW + GAP;
        if (canInvite(team)) {
            invitesY = y;
            y += 40 + Math.max(1, invitable(team).size()) * ROW + GAP;
        }
        return new Sections(createY, membersY, invitesY, y);
    }

    // ---------------------------------------------------------------- widgets

    /** Rebuilds all widgets for the current team state, keeping text, cursor and focus of the edit boxes. */
    private void widgets() {
        EditBox oldName = nameBox, oldSearch = searchBox;
        boolean nameFocused = oldName != null && getFocused() == oldName;
        boolean searchFocused = oldSearch != null && getFocused() == oldSearch;
        appliedFilter = oldSearch == null ? "" : oldSearch.getValue();
        clearWidgets();
        clearFocus();
        nameBox = null;
        searchBox = null;
        createButton = null;

        TeamInfo team = ClientQuestState.team;
        observeTeam(team);
        if (team != null)
            scroll = Math.clamp(scroll, 0, maxScroll(sections(team).height()));
        fixedButton(backX(), PAD + 9, BACK_WIDTH, 16, QuestText.t("team.back"), b -> onClose());
        if (team != null) buildTeam(team, oldName, oldSearch);

        if (nameFocused && nameBox != null) setInitialFocus(nameBox);
        else if (searchFocused && searchBox != null) setInitialFocus(searchBox);
    }

    private void observeTeam(@Nullable TeamInfo team) {
        if (team != seenTeam) {
            seenTeam = team;
            inviteSeenAt = System.currentTimeMillis();
        }
    }

    private void buildTeam(TeamInfo team, @Nullable EditBox oldName, @Nullable EditBox oldSearch) {
        Sections s = sections(team);
        if (team.invite() != null) buildBanner(team.invite());
        if (s.createY() >= 0) buildCreate(s.createY(), oldName);
        buildMembers(team, s.membersY());
        if (s.invitesY() >= 0) buildInvites(team, s.invitesY(), oldSearch);
        if (!team.solo()) {
            fixedButton(
                    innerLeft(),
                    height - PAD - 34,
                    innerWidth(),
                    16,
                    QuestText.t("team.leave"),
                    b -> confirmLeave(team));
        }
    }

    private void buildBanner(TeamInfo.Invite invite) {
        int rel = bannerLines(invite) * 10 + 22;
        int right = rowRight() - 6;
        button(
                right - 56,
                rel,
                56,
                16,
                QuestText.t("team.accept"),
                b -> ClientQuestState.action("team_accept", "", ""));
        button(
                right - 116,
                rel,
                56,
                16,
                QuestText.t("team.decline"),
                b -> ClientQuestState.action("team_decline", "", ""));
    }

    private void buildCreate(int rel, @Nullable EditBox oldName) {
        int rowRel = rel + 16 + explainLines() * 10;
        int boxWidth = innerWidth() - 64;
        nameBox = editBox(oldName, innerLeft(), rowRel, boxWidth, 18, QuestText.t("team.name_hint"));
        createButton =
                button(innerLeft() + boxWidth + 4, rowRel, 60, 18, QuestText.t("team.create"), b -> createTeam());
        createButton.active = !nameBox.getValue().isBlank();
    }

    private void createTeam() {
        if (nameBox == null) return;
        String name = nameBox.getValue().strip();
        if (!name.isEmpty()) ClientQuestState.action("team_create", name, "");
    }

    private void buildMembers(TeamInfo team, int rel) {
        List<Member> members = team.members();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (!canKick(team, member)) continue;
            int rowRel = rel + HEADING + i * ROW + 2;
            button(removeX(), rowRel, 56, 14, QuestText.t("team.remove"), b -> confirmKick(member));
        }
    }

    private void buildInvites(TeamInfo team, int rel, @Nullable EditBox oldSearch) {
        searchBox = editBox(oldSearch, innerLeft(), rel + HEADING, innerWidth(), 18, QuestText.t("team.search_hint"));
        List<Member> candidates = invitable(team);
        for (int i = 0; i < candidates.size(); i++) {
            Member member = candidates.get(i);
            String id = member.id().toString();
            int rowRel = rel + 40 + i * ROW + 2;
            if (team.invited().contains(member.id())) {
                button(
                        rowRight() - 52 - 4 - 72,
                        rowRel,
                        72,
                        14,
                        QuestText.t("team.invite_again"),
                        b -> ClientQuestState.action("team_invite", id, ""));
                button(
                        rowRight() - 52,
                        rowRel,
                        52,
                        14,
                        QuestText.t("team.cancel"),
                        b -> ClientQuestState.action("team_cancel", id, ""));
            } else {
                button(
                        rowRight() - 52,
                        rowRel,
                        52,
                        14,
                        QuestText.t("team.invite"),
                        b -> ClientQuestState.action("team_invite", id, ""));
            }
        }
    }

    /** A button positioned relative to the scrolling content; hidden while it is not fully inside the view. */
    private QuestButton button(int x, int rel, int w, int h, String label, Button.OnPress press) {
        int y = contentY(rel);
        QuestButton button = addRenderableWidget(new QuestButton(this::theme, x, y, w, h, label, press));
        button.visible = fits(y, h);
        return button;
    }

    /** A button in the header or footer, which never scrolls. */
    private void fixedButton(int x, int y, int w, int h, String label, Button.OnPress press) {
        addRenderableWidget(new QuestButton(this::theme, x, y, w, h, label, press));
    }

    private EditBox editBox(@Nullable EditBox old, int x, int rel, int w, int h, String hint) {
        int y = contentY(rel);
        EditBox box = addRenderableWidget(new EditBox(font, x, y, w, h, old, Component.literal(hint)));
        box.setHint(Component.literal(hint));
        box.setMaxLength(NAME_MAX);
        if (old != null) box.setCursorPosition(old.getCursorPosition());
        box.visible = fits(y, h);
        return box;
    }

    // ---------------------------------------------------------------- confirmations

    private void confirmKick(Member member) {
        String name = displayName(member);
        String id = member.id().toString();
        minecraft.gui.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) ClientQuestState.action("team_kick", id, "");
                    minecraft.gui.setScreen(this);
                },
                Component.translatable("iwantquests.ui.team.confirm_remove_title"),
                Component.translatable("iwantquests.ui.team.confirm_remove", name)));
    }

    private void confirmLeave(TeamInfo team) {
        String title = teamTitle(team);
        minecraft.gui.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) ClientQuestState.action("team_leave", "", "");
                    minecraft.gui.setScreen(this);
                },
                Component.translatable("iwantquests.ui.team.confirm_leave_title"),
                Component.translatable("iwantquests.ui.team.confirm_leave", title)));
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        QuestTheme t = theme();
        if (t.blur && ClientConfig.blur()) extractBlurredBackground(g);
        g.fill(0, 0, width, height, scaledAlpha(t.background, ClientConfig.backgroundOpacity()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        QuestTheme t = theme();
        TeamInfo team = ClientQuestState.team;
        int px = panelX(), pw = panelWidth(), top = PAD, bottom = height - PAD;
        QuestDraw.panel(g, t.panelSprite, px, top, pw, bottom - top, t.panel | 0xff000000, t.border);
        g.fill(px + MARGIN, top + HEADER - 1, px + pw - MARGIN, top + HEADER, t.border);
        drawHeader(g, t, team);
        if (team == null) {
            qt.drawCentered(g, QuestText.t("team.loading_team"), px + pw / 2, viewTop() + 20, t.muted);
        } else {
            drawContent(g, t, team);
        }
        drawFooter(g, t);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void drawHeader(GuiGraphicsExtractor g, QuestTheme t, @Nullable TeamInfo team) {
        String title = team == null ? QuestText.t("team") : team.solo() ? QuestText.t("team.solo") : teamTitle(team);
        int members = team == null ? 0 : team.members().size();
        String count = team == null
                ? QuestText.t("team.loading")
                : members == 1 ? QuestText.t("team.members.one") : QuestText.t("team.members.many", members);
        int maxWidth = Math.max(0, backX() - 8 - innerLeft());
        qt.drawClipped(g, title, innerLeft(), PAD + 6, maxWidth, t.text);
        qt.drawClipped(g, count, innerLeft(), PAD + 17, maxWidth, t.subtle);
    }

    private void drawContent(GuiGraphicsExtractor g, QuestTheme t, TeamInfo team) {
        Sections s = sections(team);
        g.enableScissor(panelX() + 1, viewTop(), panelRight() - 4, viewBottom());
        if (team.invite() != null) drawBanner(g, t, team.invite());
        if (s.createY() >= 0) drawCreate(g, t, contentY(s.createY()));
        drawMembers(g, t, team, contentY(s.membersY()));
        if (s.invitesY() >= 0) drawInvites(g, t, team, contentY(s.invitesY()));
        g.disableScissor();
        drawScrollbar(g, t, s.height());
    }

    private void drawBanner(GuiGraphicsExtractor g, QuestTheme t, TeamInfo.Invite invite) {
        int x = innerLeft(), w = innerWidth(), y = contentY(0);
        int lines = bannerLines(invite);
        int h = lines * 10 + 44;
        g.fill(x, y, x + w, y + h, (t.ready & 0xffffff) | 0x26000000);
        g.outline(x, y, w, h, t.ready);
        int ty = y + 6;
        for (var line : qt.wrap(bannerText(invite), w - 12)) {
            qt.drawLine(g, line, x + 6, ty, t.text);
            ty += 10;
        }
        qt.draw(g, expiresText(invite), x + 6, ty + 2, t.subtle);
    }

    private String expiresText(TeamInfo.Invite invite) {
        long remaining = Math.max(0, invite.expiresInMs() - (System.currentTimeMillis() - inviteSeenAt));
        if (remaining == 0) return QuestText.t("team.expired");
        long seconds = (remaining + 999) / 1000;
        return QuestText.t("team.expires", seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60));
    }

    private void drawCreate(GuiGraphicsExtractor g, QuestTheme t, int y) {
        qt.draw(g, QuestText.t("team.create_heading"), innerLeft(), y, t.heading);
        int ty = y + 12;
        for (var line : qt.wrap(QuestText.t("team.explain"), innerWidth())) {
            qt.drawLine(g, line, innerLeft(), ty, t.muted);
            ty += 10;
        }
    }

    private void drawMembers(GuiGraphicsExtractor g, QuestTheme t, TeamInfo team, int y) {
        qt.draw(g, QuestText.t("team.members_heading"), innerLeft(), y, t.heading);
        List<Member> members = team.members();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            int rowY = y + HEADING + i * ROW;
            drawFace(g, member, innerLeft(), rowY + 3);
            drawMemberStatus(g, t, team, member, rowY);
        }
    }

    /** Draws the name, then right-to-left: remove slot, online state, owner tag. */
    private void drawMemberStatus(GuiGraphicsExtractor g, QuestTheme t, TeamInfo team, Member member, int rowY) {
        int right = canKick(team, member) ? removeX() - 6 : rowRight();
        boolean online = team.online().contains(member.id());
        if (!online) {
            String offline = QuestText.t("team.offline");
            qt.drawRight(g, offline, right, rowY + 5, t.subtle);
            right -= qt.width(offline) + 4;
        }
        right -= 4;
        QuestDraw.geometry(g, "circle", right, rowY + 7, 5, 5, online ? t.complete : t.subtle);
        right -= 6;
        if (member.id().equals(team.owner())) {
            String owner = QuestText.t("team.owner");
            qt.drawRight(g, owner, right, rowY + 5, t.ready);
            right -= qt.width(owner) + 6;
        }
        int nameX = innerLeft() + FACE + FACE_GAP;
        qt.drawClipped(g, displayName(member), nameX, rowY + 5, Math.max(0, right - nameX), t.text);
    }

    private void drawInvites(GuiGraphicsExtractor g, QuestTheme t, TeamInfo team, int y) {
        qt.draw(g, QuestText.t("team.invite_heading"), innerLeft(), y, t.heading);
        List<Member> candidates = invitable(team);
        if (candidates.isEmpty()) {
            String empty = QuestText.t(team.invitable().isEmpty() ? "team.nobody_online" : "team.no_match");
            qt.draw(g, empty, innerLeft(), y + 40, t.subtle);
            return;
        }
        for (int i = 0; i < candidates.size(); i++) {
            Member member = candidates.get(i);
            int rowY = y + 40 + i * ROW;
            drawFace(g, member, innerLeft(), rowY + 3);
            boolean invited = team.invited().contains(member.id());
            int nameRight = rowRight() - 52 - 6;
            if (invited) {
                int againX = rowRight() - 52 - 4 - 72;
                String label = QuestText.t("team.invited");
                nameRight = againX - 6 - qt.width(label) - 6;
                qt.drawRight(g, label, againX - 6, rowY + 5, t.subtle);
            }
            int nameX = innerLeft() + FACE + FACE_GAP;
            qt.drawClipped(g, displayName(member), nameX, rowY + 5, Math.max(0, nameRight - nameX), t.text);
        }
    }

    private void drawFace(GuiGraphicsExtractor g, Member member, int x, int y) {
        g.fill(x - 1, y - 1, x + FACE + 1, y + FACE + 1, 0xff000000);
        PlayerFaceExtractor.extractRenderState(g, skin(member.id()), x, y, FACE);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, QuestTheme t, int contentHeight) {
        int viewHeight = viewBottom() - viewTop();
        contentHeight += TOP_PAD;
        if (contentHeight <= viewHeight) return;
        int trackX = panelRight() - 4;
        g.fill(trackX, viewTop(), trackX + 2, viewBottom(), t.progressBack);
        int thumb = Math.max(12, viewHeight * viewHeight / contentHeight);
        int thumbY = viewTop() + scroll * (viewHeight - thumb) / Math.max(1, contentHeight - viewHeight);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, t.subtle);
    }

    private void drawFooter(GuiGraphicsExtractor g, QuestTheme t) {
        int bottom = height - PAD;
        String feedback = ClientQuestState.feedback;
        if (feedback != null && !feedback.isEmpty()) {
            qt.drawClipped(g, feedback, innerLeft(), bottom - 12, innerWidth(), t.muted);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x < panelX() || x >= panelRight()) return super.mouseScrolled(x, y, scrollX, scrollY);
        TeamInfo team = ClientQuestState.team;
        int max = team == null ? 0 : maxScroll(sections(team).height());
        scroll = Math.clamp(scroll - (int) Math.round(scrollY * SCROLL_STEP), 0, max);
        widgets();
        return true;
    }
}
