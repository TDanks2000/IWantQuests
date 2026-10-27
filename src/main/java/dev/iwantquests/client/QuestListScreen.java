package dev.iwantquests.client;

import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.client.ui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Searchable, paginated chooser shared by item selection, templates, search and publication review. */
abstract class QuestListScreen extends Screen {
    protected record Entry(String title, String detail, ItemStack icon, Runnable choose, boolean enabled) {
        Entry(String title, String detail, ItemStack icon, Runnable choose) {
            this(title, detail, icon, choose, true);
        }
    }

    protected final QuestBookScreen parent;
    protected final QuestText qt;
    protected String query = "";
    private EditBox search;
    private int page;
    private List<Entry> shown = List.of();

    protected QuestListScreen(QuestBookScreen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
        qt = new QuestText(parent::theme);
    }

    protected abstract List<Entry> entries();

    protected String modeLabel() {
        return "";
    }

    protected void nextMode() {}

    protected boolean modeEnabled() {
        return true;
    }

    protected String footer() {
        return QuestText.t(shown.size() == 1 ? "chooser.results.one" : "chooser.results.many", shown.size());
    }

    protected int panelWidth() {
        return Math.min(520, width - 16);
    }

    protected int left() {
        return (width - panelWidth()) / 2 + 10;
    }

    protected int innerWidth() {
        return panelWidth() - 20;
    }

    private int rows() {
        return Math.max(1, (height - 112) / 30);
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
    protected void init() {
        rebuild(true);
    }

    protected void refresh() {
        page = 0;
        rebuild(search != null && search.isFocused());
    }

    private void rebuild(boolean focus) {
        clearWidgets();
        shown = entries();
        page = Math.clamp(page, 0, Math.max(0, (shown.size() - 1) / rows()));
        addRenderableWidget(new QuestButton(
                parent::theme, left() + innerWidth() - 48, 16, 48, 18, QuestText.t("chooser.back"), b -> onClose()));
        int modeWidth = modeLabel().isEmpty() ? 0 : Math.min(144, innerWidth() / 2);
        search = addRenderableWidget(new EditBox(
                minecraft.font,
                left(),
                44,
                innerWidth() - modeWidth - (modeWidth > 0 ? 6 : 0),
                18,
                Component.literal(QuestText.t("chooser.search"))));
        search.setMaxLength(128);
        search.setValue(query);
        search.setHint(Component.literal(QuestText.t("chooser.search")));
        search.setResponder(text -> {
            query = text;
            page = 0;
            rebuild(true);
        });
        if (focus) setFocused(search);
        if (modeWidth > 0) {
            var mode = addRenderableWidget(new QuestButton(
                    parent::theme, left() + innerWidth() - modeWidth, 44, modeWidth, 18, modeLabel(), b -> {
                        nextMode();
                        refresh();
                    }));
            mode.active = modeEnabled();
        }
        for (int i = 0; i < rows(); i++) {
            int index = page * rows() + i;
            if (index >= shown.size()) break;
            Entry entry = shown.get(index);
            int y = 72 + i * 30;
            var row = addRenderableWidget(
                    new QuestButton(
                            parent::theme,
                            left(),
                            y,
                            innerWidth(),
                            28,
                            entry.title(),
                            b -> entry.choose().run()) {
                        @Override
                        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
                            QuestTheme t = parent.theme();
                            boolean hot = active && isHoveredOrFocused();
                            QuestDraw.box(
                                    g,
                                    getX(),
                                    getY(),
                                    getWidth(),
                                    getHeight(),
                                    hot ? t.buttonHoverFill : t.buttonFill,
                                    hot ? t.accent : t.border);
                            int x = getX() + 6;
                            if (!entry.icon().isEmpty()) {
                                g.item(entry.icon(), x, getY() + 6);
                                x += 23;
                            }
                            qt.drawClipped(
                                    g,
                                    entry.title(),
                                    x,
                                    getY() + 4,
                                    getWidth() - (x - getX()) - 6,
                                    active ? t.text : t.muted);
                            qt.drawClipped(g, entry.detail(), x, getY() + 16, getWidth() - (x - getX()) - 6, t.subtle);
                            if (isFocused())
                                g.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, t.accent);
                        }
                    });
            row.active = entry.enabled();
            row.setTooltip(Tooltip.create(Component.literal(entry.title() + "\n" + entry.detail())));
        }
        var previous = addRenderableWidget(new QuestButton(parent::theme, left(), height - 32, 30, 18, "<", b -> {
            page--;
            rebuild(false);
        }));
        previous.active = page > 0;
        var next = addRenderableWidget(
                new QuestButton(parent::theme, left() + innerWidth() - 30, height - 32, 30, 18, ">", b -> {
                    page++;
                    rebuild(false);
                }));
        next.active = (page + 1) * rows() < shown.size();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.isConfirmation() && search != null && search.isFocused() && !shown.isEmpty()) {
            Entry entry = shown.get(page * rows());
            if (entry.enabled()) entry.choose().run();
            return true;
        }
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            page += e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            rebuild(false);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy != 0 && shown.size() > rows()) {
            page += sy < 0 ? 1 : -1;
            rebuild(false);
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, parent.theme().background | 0xff000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        var t = parent.theme();
        QuestDraw.box(g, left() - 10, 8, panelWidth(), height - 16, t.panel | 0xff000000, t.border);
        qt.drawClipped(g, getTitle().getString(), left(), 20, innerWidth() - 56, t.heading);
        if (shown.isEmpty()) qt.drawCentered(g, QuestText.t("chooser.empty"), width / 2, 90, t.muted);
        qt.drawCentered(
                g,
                qt.clip(
                        footer() + " · " + (page + 1) + "/" + Math.max(1, (shown.size() + rows() - 1) / rows()),
                        innerWidth() - 76),
                width / 2,
                height - 26,
                t.subtle);
        super.extractRenderState(g, mx, my, partial);
    }
}
