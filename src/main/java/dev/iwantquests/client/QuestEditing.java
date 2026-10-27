package dev.iwantquests.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.client.ui.QuestButton;
import dev.iwantquests.client.ui.QuestDraw;
import dev.iwantquests.client.ui.QuestText;
import dev.iwantquests.quest.QuestPack.Chapter;
import dev.iwantquests.quest.QuestPack.Quest;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The visual quest editor: tools, selection, dragging and the requests that turn those into server edits.
 * <p>
 * The server owns the quest files. Every gesture ends in one request, and the quest book only shows what the server
 * sends back. While a request is in flight, dragged nodes keep the position the player dropped them at
 * ({@link #overrides}) so they do not jump back for a moment.
 */
public final class QuestEditing {
    public enum Tool {
        SELECT,
        LINK,
        ADD
    }

    private enum Drag {
        NONE,
        MOVE,
        BOX
    }

    /** World units between snap positions; nodes are 32 units wide, so this gives a clean four-step grid per node. */
    static final int GRID = 8;

    private static final int DRAG_THRESHOLD = 3, PALETTE_WIDTH = 82, BUTTON = 18, GAP = 3, NUDGE_DELAY = 8;

    private final QuestBookScreen screen;
    private final QuestInspector inspector;
    private final QuestText qt;

    private Tool tool = Tool.SELECT;
    private boolean snap = true;
    private final Set<String> selection = new LinkedHashSet<>();
    private @Nullable String linkSource;

    private Drag drag = Drag.NONE;
    private boolean moved, pressedWasSelected, pressShift;
    private @Nullable String pressedNode;
    private double pressX, pressY;
    private final Map<String, double[]> origins = new HashMap<>();
    private int boxX, boxY, boxEndX, boxEndY;

    /** Positions shown instead of the definitions' while a move is on its way to the server and back. */
    private final Map<String, double[]> overrides = new HashMap<>();

    private int nudgeTicks, seenResult = ClientQuestState.editResultSerial;
    private boolean failed;
    private static List<Quest> clipboard = List.of();

    QuestEditing(QuestBookScreen screen) {
        this.screen = screen;
        this.qt = screen.qt;
        this.inspector = new QuestInspector(screen, this);
    }

    // ---------------------------------------------------------------- state

    public Tool tool() {
        return tool;
    }

    public Set<String> selection() {
        return Collections.unmodifiableSet(selection);
    }

    public boolean snapping() {
        return snap;
    }

    boolean failed() {
        return failed;
    }

    QuestInspector inspector() {
        return inspector;
    }

    boolean isSelected(Quest q) {
        return selection.contains(q.id());
    }

    boolean editable(Quest q) {
        return ClientQuestState.editableQuests.contains(q.id());
    }

    boolean editable(Chapter c) {
        return ClientQuestState.editableChapters.contains(c.id());
    }

    double x(Quest q) {
        double[] override = overrides.get(q.id());
        return override == null ? q.x() : override[0];
    }

    double y(Quest q) {
        double[] override = overrides.get(q.id());
        return override == null ? q.y() : override[1];
    }

    /** The one selected quest, or null when none or several are selected. */
    @Nullable
    Quest single() {
        return selection.size() == 1 ? screen.findQuest(selection.iterator().next()) : null;
    }

    List<Quest> selected() {
        List<Quest> result = new ArrayList<>();
        for (String id : selection) {
            Quest q = screen.findQuest(id);
            if (q != null) result.add(q);
        }
        return result;
    }

    boolean hoverAllowed() {
        return drag == Drag.NONE || !moved;
    }

    void say(String text) {
        ClientQuestState.feedback = text;
        ClientQuestState.feedbackSerial++;
    }

    private void selectionChanged() {
        inspector.resetScroll();
        screen.refreshWidgets();
    }

    void select(@Nullable Quest q) {
        selection.clear();
        if (q != null) selection.add(q.id());
        selectionChanged();
    }

    void setTool(Tool next) {
        tool = next;
        linkSource = null;
        screen.refreshWidgets();
    }

    void chapterChanged() {
        inspector.commitAll();
        flushMove();
        drag = Drag.NONE;
        selection.clear();
        linkSource = null;
        overrides.clear();
        inspector.resetScroll();
    }

    /** New definitions arrived: forget selected quests that no longer exist. */
    void dataChanged() {
        Set<String> existing = new HashSet<>();
        for (Quest q : screen.quests()) existing.add(q.id());
        selection.retainAll(existing);
        if (linkSource != null && !existing.contains(linkSource)) linkSource = null;
    }

    void close() {
        inspector.commitAll();
        flushMove();
        selection.clear();
        overrides.clear();
        linkSource = null;
    }

    /** Saves keyboard movement even when the book is closed before the repeat delay expires. */
    private void flushMove() {
        if (nudgeTicks > 0) {
            nudgeTicks = 0;
            commitMove();
        }
    }

    void savePending() {
        inspector.commitAll();
        flushMove();
    }

    // ---------------------------------------------------------------- widgets

    private int paletteX() {
        return screen.canvasLeft() + 8;
    }

    private int paletteY() {
        return screen.treeTop() + 40;
    }

    private int toolHeight() {
        return screen.height < 300 ? 16 : BUTTON;
    }

    private int paletteHeight() {
        return 7 * (toolHeight() + GAP) + GAP * 6 + 16;
    }

    void addWidgets() {
        int x = paletteX() + GAP, y = paletteY() + GAP + 13;
        int row = toolHeight() + GAP;
        palette(x, y, "editor.tool.select", "editor.tip.select", tool == Tool.SELECT, true, b -> setTool(Tool.SELECT));
        y += row;
        palette(x, y, "editor.tool.link", "editor.tip.link", tool == Tool.LINK, true, b -> setTool(Tool.LINK));
        y += row;
        palette(x, y, "editor.tool.add", "editor.tip.add", tool == Tool.ADD, true, b -> setTool(Tool.ADD));
        y += row + GAP * 2;
        palette(x, y, "editor.delete", "editor.tip.delete", false, !selection.isEmpty(), b -> deleteSelection());
        y += row + GAP * 2;
        int half = (PALETTE_WIDTH - GAP * 3) / 2;
        palette(x, y, half, "editor.undo", "editor.tip.undo", false, ClientQuestState.editor.canUndo(), b -> undo());
        palette(
                x + half + GAP,
                y,
                half,
                "editor.redo",
                "editor.tip.redo",
                false,
                ClientQuestState.editor.canRedo(),
                b -> redo());
        y += row + GAP * 2;
        palette(x, y, snap ? "editor.snap_on" : "editor.snap_off", "editor.tip.snap", false, true, b -> toggleSnap());
        y += row;
        palette(
                x,
                y,
                "editor.new_chapter",
                "editor.tip.new_chapter",
                false,
                ClientQuestState.editsInFlight == 0,
                b -> createChapter());
        inspector.build();
    }

    private void palette(
            int x, int y, String label, String tip, boolean primary, boolean active, QuestButton.OnPress press) {
        palette(x, y, PALETTE_WIDTH - GAP * 2, label, tip, primary, active, press);
    }

    private void palette(
            int x,
            int y,
            int width,
            String label,
            String tip,
            boolean primary,
            boolean active,
            QuestButton.OnPress press) {
        var button = screen.addEditorWidget(
                new QuestButton(screen::theme, x, y, width, toolHeight(), QuestText.t(label), press));
        button.primary = primary;
        button.active = active;
        button.setTooltip(Tooltip.create(Component.literal(QuestText.t(tip))));
    }

    private boolean inPalette(double mx, double my) {
        return mx >= paletteX()
                && mx < paletteX() + PALETTE_WIDTH
                && my >= paletteY()
                && my < paletteY() + paletteHeight();
    }

    private void toggleSnap() {
        snap = !snap;
        screen.refreshWidgets();
    }

    // ---------------------------------------------------------------- requests

    private static JsonObject object(String field, JsonElement value) {
        JsonObject object = new JsonObject();
        object.add(field, value);
        return object;
    }

    void patchQuest(String id, JsonObject set) {
        patch("quests", id, set);
    }

    void patchChapter(String id, JsonObject set) {
        patch("chapters", id, set);
    }

    /** Changes several quests in one request, so they form one undo step. */
    void patchQuests(Map<String, JsonObject> sets) {
        JsonArray entries = new JsonArray();
        sets.forEach((id, set) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.add("set", set);
            entries.add(entry);
        });
        ClientQuestState.edit("patch", object("quests", entries).toString());
    }

    private void patch(String kind, String id, JsonObject set) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.add("set", set);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        ClientQuestState.edit("patch", object(kind, entries).toString());
    }

    void createChapter() {
        ClientQuestState.edit("create_chapter", "{}");
    }

    public void copy() {
        clipboard = List.copyOf(selected());
        say(QuestText.t("editor.copied", clipboard.size()));
    }

    public void duplicate() {
        paste(selected(), true);
    }

    public void paste(List<Quest> source) {
        paste(source, false);
    }

    private void paste(List<Quest> source, boolean nearby) {
        if (source.isEmpty() || screen.currentChapter() == null) return;
        savePending();
        double x = nearby
                ? source.stream().mapToDouble(Quest::x).min().orElse(0) + 48
                : ((screen.canvasLeft() + screen.canvasRight()) / 2.0 - screen.canvas.panX) / screen.canvas.zoom;
        double y = nearby
                ? source.stream().mapToDouble(Quest::y).min().orElse(0) + 48
                : ((screen.treeTop() + screen.height) / 2.0 - screen.canvas.panY) / screen.canvas.zoom;
        try {
            var position = dev.iwantquests.client.ui.QuestPlacement.find(
                    source,
                    screen.quests().stream()
                            .filter(q -> q.chapter().equals(screen.chapter))
                            .toList(),
                    x,
                    y,
                    screen.theme().nodeSize);
            var request = new dev.iwantquests.edit.QuestEditor.PasteRequest(
                    source, screen.chapter, position.x(), position.y());
            ClientQuestState.edit("paste", dev.iwantquests.quest.QuestLoader.JSON.toJson(request));
        } catch (IllegalArgumentException error) {
            say(error.getMessage());
        }
    }

    public void saveTemplate() {
        if (selection.isEmpty()) return;
        savePending();
        var request = new dev.iwantquests.edit.QuestTemplates.Request(
                selected().getFirst().title(), List.copyOf(selection));
        ClientQuestState.edit("save_template", dev.iwantquests.quest.QuestLoader.JSON.toJson(request));
    }

    void deleteChapter(Chapter c) {
        JsonArray ids = new JsonArray();
        ids.add(c.id());
        ClientQuestState.edit("delete", object("chapters", ids).toString());
    }

    void undo() {
        inspector.commitAll();
        if (ClientQuestState.editor.canUndo()) ClientQuestState.edit("undo", "{}");
    }

    void redo() {
        inspector.commitAll();
        if (ClientQuestState.editor.canRedo()) ClientQuestState.edit("redo", "{}");
    }

    void deleteSelection() {
        List<Quest> quests = selected();
        if (quests.isEmpty()) return;
        for (Quest q : quests) {
            if (!editable(q)) {
                say(QuestText.t("editor.readonly_quest", q.title()));
                return;
            }
        }
        JsonArray ids = new JsonArray();
        quests.forEach(q -> ids.add(q.id()));
        ClientQuestState.edit("delete", object("quests", ids).toString());
        selection.clear();
        selectionChanged();
    }

    private void addAt(double mx, double my, boolean linkToSelected) {
        Chapter chapter = screen.currentChapter();
        if (chapter == null) {
            say(QuestText.t("editor.no_chapter"));
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("chapter", chapter.id());
        body.addProperty("x", snap((mx - screen.canvas.panX) / screen.canvas.zoom, false));
        body.addProperty("y", snap((my - screen.canvas.panY) / screen.canvas.zoom, false));
        Quest parent = single();
        if (linkToSelected && parent != null) {
            JsonArray prerequisites = new JsonArray();
            prerequisites.add(parent.id());
            body.add("prerequisites", prerequisites);
        }
        ClientQuestState.edit("create_quest", body.toString());
    }

    private double snap(double world, boolean invert) {
        return snap != invert ? Math.round(world / GRID) * (double) GRID : Math.round(world);
    }

    /** Sends the positions of every selected quest that differs from its definition, in one undoable step. */
    private void commitMove() {
        JsonArray entries = new JsonArray();
        for (Map.Entry<String, double[]> entry : overrides.entrySet()) {
            Quest q = screen.findQuest(entry.getKey());
            if (q == null || (q.x() == entry.getValue()[0] && q.y() == entry.getValue()[1])) continue;
            JsonObject set = new JsonObject();
            set.addProperty("x", entry.getValue()[0]);
            set.addProperty("y", entry.getValue()[1]);
            JsonObject item = new JsonObject();
            item.addProperty("id", q.id());
            item.add("set", set);
            entries.add(item);
        }
        if (entries.isEmpty()) {
            overrides.clear();
            return;
        }
        ClientQuestState.edit("patch", object("quests", entries).toString());
    }

    // ---------------------------------------------------------------- input

    boolean mouseClicked(MouseButtonEvent e) {
        double mx = e.x(), my = e.y();
        inspector.commitAll();
        screen.setFocused(null);
        if (inspector.contains(mx, my) || inPalette(mx, my)) return true;
        if (!screen.inCanvas(mx, my)) return false;
        Quest hit = screen.nodeAt(screen.theme(), screen.visibleQuests(), (int) mx, (int) my);
        if (e.button() == 1) {
            if (hit == null) addAt(mx, my, e.hasShiftDown());
            return true;
        }
        if (e.button() != 0) return false;
        switch (tool) {
            case ADD -> {
                if (hit != null) select(hit);
                else addAt(mx, my, e.hasShiftDown());
            }
            case LINK -> link(hit);
            default -> press(hit, mx, my, e.hasShiftDown());
        }
        return true;
    }

    private void press(@Nullable Quest hit, double mx, double my, boolean shift) {
        pressX = mx;
        pressY = my;
        moved = false;
        pressShift = shift;
        if (hit != null) {
            pressedNode = hit.id();
            pressedWasSelected = selection.contains(hit.id());
            if (shift) {
                if (!selection.remove(hit.id())) selection.add(hit.id());
                selectionChanged();
                drag = Drag.NONE;
                return;
            }
            if (!pressedWasSelected) {
                selection.clear();
                selection.add(hit.id());
                selectionChanged();
            }
            origins.clear();
            for (Quest q : selected()) origins.put(q.id(), new double[] {x(q), y(q)});
            drag = Drag.MOVE;
            return;
        }
        pressedNode = null;
        if (shift) {
            drag = Drag.BOX;
            boxX = boxEndX = (int) mx;
            boxY = boxEndY = (int) my;
            return;
        }
        drag = Drag.NONE;
        if (!selection.isEmpty()) {
            selection.clear();
            selectionChanged();
        }
        screen.beginPan();
    }

    private void link(@Nullable Quest hit) {
        if (hit == null) {
            linkSource = null;
            screen.beginPan();
            return;
        }
        if (linkSource == null) {
            linkSource = hit.id();
            select(hit);
            return;
        }
        String parent = linkSource;
        linkSource = null;
        if (parent.equals(hit.id())) return;
        if (!editable(hit)) {
            say(QuestText.t("editor.readonly_quest", hit.title()));
            return;
        }
        List<String> prerequisites = new ArrayList<>(hit.prerequisites() == null ? List.of() : hit.prerequisites());
        if (!prerequisites.remove(parent)) prerequisites.add(parent);
        JsonArray array = new JsonArray();
        prerequisites.forEach(array::add);
        patchQuest(hit.id(), object("prerequisites", array));
    }

    boolean mouseDragged(MouseButtonEvent e) {
        if (drag == Drag.BOX) {
            boxEndX = (int) e.x();
            boxEndY = (int) e.y();
            return true;
        }
        if (drag != Drag.MOVE || pressedNode == null) return false;
        double dx = e.x() - pressX, dy = e.y() - pressY;
        if (!moved) {
            if (Math.hypot(dx, dy) < DRAG_THRESHOLD) return true;
            for (Quest q : selected()) {
                if (!editable(q)) {
                    say(QuestText.t("editor.readonly_quest", q.title()));
                    drag = Drag.NONE;
                    return true;
                }
            }
            moved = true;
        }
        double[] anchor = origins.get(pressedNode);
        if (anchor == null) return true;
        double zoom = screen.canvas.zoom;
        double nx = snap(anchor[0] + dx / zoom, e.hasAltDown()), ny = snap(anchor[1] + dy / zoom, e.hasAltDown());
        for (Map.Entry<String, double[]> origin : origins.entrySet()) {
            overrides.put(
                    origin.getKey(),
                    new double[] {origin.getValue()[0] + nx - anchor[0], origin.getValue()[1] + ny - anchor[1]});
        }
        return true;
    }

    void mouseReleased(MouseButtonEvent e) {
        Drag finished = drag;
        drag = Drag.NONE;
        if (finished == Drag.MOVE) {
            if (moved) {
                commitMove();
            } else if (pressedWasSelected && !pressShift && selection.size() > 1 && pressedNode != null) {
                selection.clear();
                selection.add(pressedNode);
                selectionChanged();
            }
        } else if (finished == Drag.BOX) {
            int x0 = Math.min(boxX, boxEndX),
                    x1 = Math.max(boxX, boxEndX),
                    y0 = Math.min(boxY, boxEndY),
                    y1 = Math.max(boxY, boxEndY);
            for (Quest q : screen.visibleQuests()) {
                int cx = screen.nodeCentreX(q), cy = screen.nodeCentreY(q);
                if (cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1) selection.add(q.id());
            }
            selectionChanged();
        }
        moved = false;
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!inspector.contains(mx, my)) return false;
        inspector.scroll((int) Math.round(-scrollY * 24));
        return true;
    }

    boolean keyPressed(KeyEvent e) {
        if (e.hasControlDownWithQuirk() && e.key() == GLFW.GLFW_KEY_S) {
            savePending();
            screen.setFocused(null);
            return true;
        }
        var focus = screen.getFocused();
        if (focus instanceof EditBox || focus instanceof MultiLineEditBox) {
            if (e.isEscape()) {
                inspector.revert();
                return true;
            }
            if (e.isConfirmation() && focus instanceof EditBox) {
                inspector.commitAll();
                screen.setFocused(null);
                return true;
            }
            if (focus.keyPressed(e)) return true;
            return !e.isCycleFocus();
        }
        if (e.hasControlDownWithQuirk()) {
            if (e.key() == GLFW.GLFW_KEY_C) {
                copy();
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_V) {
                paste(clipboard);
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_D) {
                duplicate();
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_Z) {
                if (e.hasShiftDown()) redo();
                else undo();
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
            if (e.isSelectAll()) {
                screen.visibleQuests().forEach(q -> selection.add(q.id()));
                selectionChanged();
                return true;
            }
            return false;
        }
        if (e.isEscape()) {
            if (linkSource != null) {
                linkSource = null;
                return true;
            }
            if (!selection.isEmpty()) {
                selection.clear();
                selectionChanged();
                return true;
            }
            return false;
        }
        int key = e.key();
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelection();
            return true;
        }
        double step = e.hasShiftDown() ? 1 : GRID;
        if (e.isLeft()) return nudge(-step, 0);
        if (e.isRight()) return nudge(step, 0);
        if (e.isUp()) return nudge(0, -step);
        if (e.isDown()) return nudge(0, step);
        switch (key) {
            case GLFW.GLFW_KEY_V -> setTool(Tool.SELECT);
            case GLFW.GLFW_KEY_L -> setTool(Tool.LINK);
            case GLFW.GLFW_KEY_A -> setTool(Tool.ADD);
            case GLFW.GLFW_KEY_G -> toggleSnap();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Moves the selection a little. Rapid key repeats are sent as one undo step once the keys are released. */
    private boolean nudge(double dx, double dy) {
        List<Quest> quests = selected();
        if (quests.isEmpty()) return false;
        for (Quest q : quests) {
            if (!editable(q)) {
                say(QuestText.t("editor.readonly_quest", q.title()));
                return true;
            }
        }
        for (Quest q : quests) overrides.put(q.id(), new double[] {x(q) + dx, y(q) + dy});
        nudgeTicks = NUDGE_DELAY;
        return true;
    }

    // ---------------------------------------------------------------- tick

    void tick() {
        if (nudgeTicks > 0 && --nudgeTicks == 0) commitMove();
        inspector.tick();
        if (seenResult != ClientQuestState.editResultSerial) {
            seenResult = ClientQuestState.editResultSerial;
            failed = !ClientQuestState.editOk;
            if (ClientQuestState.editsInFlight == 0 && nudgeTicks == 0 && drag != Drag.MOVE) overrides.clear();
            String created = ClientQuestState.editSelect;
            ClientQuestState.editSelect = null;
            if (ClientQuestState.editOk && created != null) showCreated(created);
            screen.refreshWidgets();
        }
    }

    private void showCreated(String id) {
        Quest quest = screen.findQuest(id);
        if (quest != null) {
            if (!quest.chapter().equals(screen.chapter)) screen.chapter = quest.chapter();
            selection.clear();
            selection.add(id);
            tool = Tool.SELECT;
            inspector.resetScroll();
            screen.revealQuest(quest);
            return;
        }
        for (Chapter c : screen.chapters()) {
            if (c.id().equals(id)) {
                screen.selectChapter(c);
                tool = Tool.SELECT;
                return;
            }
        }
    }

    // ---------------------------------------------------------------- rendering

    void drawPanels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        QuestTheme t = screen.theme();
        QuestDraw.box(
                g,
                paletteX(),
                paletteY(),
                PALETTE_WIDTH,
                paletteHeight(),
                QuestDraw.mix(t.panel | 0xff000000, 0xff000000, .15f),
                t.border);
        qt.draw(g, QuestText.t("editor.tools"), paletteX() + GAP + 2, paletteY() + GAP + 1, t.subtle);
        String hint = switch (tool) {
            case SELECT -> QuestText.t("editor.hint.select");
            case LINK -> QuestText.t(linkSource == null ? "editor.hint.link_start" : "editor.hint.link_end");
            case ADD -> QuestText.t("editor.hint.add");
        };
        int hintX = paletteX() + PALETTE_WIDTH + GAP + 4;
        qt.drawClipped(g, hint, hintX, paletteY() + 5, Math.max(0, screen.canvasRight() - hintX - 8), t.subtle);
        inspector.draw(g, t, mouseX, mouseY);
    }

    /** A dot grid fixed to the canvas rather than the screen, so it shows where nodes will snap. */
    void drawGrid(GuiGraphicsExtractor g, QuestTheme t) {
        double world = GRID * 4;
        while (world * screen.canvas.zoom < 14) world *= 2;
        double step = world * screen.canvas.zoom;
        int dot = QuestDraw.mix(t.border, t.background | 0xff000000, .35f);
        int left = screen.canvasLeft(), right = screen.canvasRight(), top = screen.treeTop();
        double startX = screen.canvas.panX + Math.floor((left - screen.canvas.panX) / step) * step;
        double startY = screen.canvas.panY + Math.floor((top - screen.canvas.panY) / step) * step;
        for (double sx = startX; sx < right; sx += step) {
            for (double sy = startY; sy < screen.height; sy += step) {
                int px = (int) Math.round(sx), py = (int) Math.round(sy);
                g.fill(px, py, px + 1, py + 1, dot);
            }
        }
    }

    /** Selection rectangle, alignment guides, the link in progress and the new-quest preview, on top of the nodes. */
    void drawCanvas(GuiGraphicsExtractor g, QuestTheme t, int mouseX, int mouseY) {
        if (drag == Drag.MOVE && moved) drawGuides(g, t);
        if (drag == Drag.BOX) {
            int x0 = Math.min(boxX, boxEndX),
                    x1 = Math.max(boxX, boxEndX),
                    y0 = Math.min(boxY, boxEndY),
                    y1 = Math.max(boxY, boxEndY);
            g.fill(x0, y0, x1, y1, withAlpha(t.accent, 0x28));
            g.outline(x0, y0, x1 - x0, y1 - y0, withAlpha(t.accent, 0xc0));
        }
        if (linkSource != null) {
            Quest source = screen.findQuest(linkSource);
            if (source != null) {
                int sx = screen.nodeCentreX(source), sy = screen.nodeCentreY(source);
                QuestDraw.direct(g, sx, sy, mouseX, mouseY, 2, 0, withAlpha(t.accent, 0xd0));
            }
        }
        if (tool == Tool.ADD
                && drag == Drag.NONE
                && screen.inCanvas(mouseX, mouseY)
                && !inPalette(mouseX, mouseY)
                && screen.nodeAt(t, screen.visibleQuests(), mouseX, mouseY) == null) {
            double wx = snap((mouseX - screen.canvas.panX) / screen.canvas.zoom, false);
            double wy = snap((mouseY - screen.canvas.panY) / screen.canvas.zoom, false);
            int size = Math.max(4, (int) Math.round(t.nodeSize * screen.canvas.zoom));
            int cx = (int) Math.round(screen.canvas.screenX(wx)), cy = (int) Math.round(screen.canvas.screenY(wy));
            QuestDraw.framed(
                    g,
                    "circle",
                    cx - size / 2,
                    cy - size / 2,
                    size,
                    size,
                    2,
                    withAlpha(t.accent, 0xa0),
                    withAlpha(t.nodeFace, 0x90));
        }
    }

    /** Thin lines through the dragged node and any other node it lines up with exactly. */
    private void drawGuides(GuiGraphicsExtractor g, QuestTheme t) {
        Quest primary = pressedNode == null ? null : screen.findQuest(pressedNode);
        if (primary == null) return;
        double px = x(primary), py = y(primary);
        int colour = withAlpha(t.accent, 0x90);
        for (Quest other : screen.visibleQuests()) {
            if (other.id().equals(pressedNode) || overrides.containsKey(other.id())) continue;
            if (other.x() == px) {
                int sx = screen.nodeCentreX(primary);
                int y0 = Math.min(screen.nodeCentreY(primary), screen.nodeCentreY(other)),
                        y1 = Math.max(screen.nodeCentreY(primary), screen.nodeCentreY(other));
                g.fill(sx, y0, sx + 1, y1, colour);
            }
            if (other.y() == py) {
                int sy = screen.nodeCentreY(primary);
                int x0 = Math.min(screen.nodeCentreX(primary), screen.nodeCentreX(other)),
                        x1 = Math.max(screen.nodeCentreX(primary), screen.nodeCentreX(other));
                g.fill(x0, sy, x1, sy + 1, colour);
            }
        }
    }

    /** Marks read-only quests and the link source on top of an already drawn node. */
    void decorate(GuiGraphicsExtractor g, QuestTheme t, Quest q, QuestTheme.Shape shape, int x, int y, int s) {
        if (!editable(q)) QuestDraw.geometry(g, shape.geometry(), x, y, s, s, 0x7010151c);
        if (q.id().equals(linkSource))
            QuestDraw.geometry(g, shape.geometry(), x - 4, y - 4, s + 8, s + 8, withAlpha(t.accent, 0x60));
    }

    List<Component> tooltip(QuestTheme t, Quest q) {
        List<Component> lines = new ArrayList<>();
        lines.add(qt.component(Component.literal(q.title()).withColor(t.text & 0xffffff)));
        lines.add(qt.component(Component.literal(q.id()).withColor(t.subtle & 0xffffff)));
        lines.add(qt.component(Component.literal(QuestText.t("editor.position", Math.round(x(q)), Math.round(y(q))))
                .withColor(t.muted & 0xffffff)));
        if (!editable(q))
            lines.add(qt.component(
                    Component.literal(QuestText.t("editor.readonly_short")).withColor(t.locked & 0xffffff)));
        return lines;
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0xffffff);
    }
}
