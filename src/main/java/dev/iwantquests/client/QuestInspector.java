package dev.iwantquests.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.client.ui.QuestButton;
import dev.iwantquests.client.ui.QuestDraw;
import dev.iwantquests.client.ui.QuestText;
import dev.iwantquests.client.ui.QuestViews;
import dev.iwantquests.objective.ObjectiveRegistry;
import dev.iwantquests.quest.QuestLoader;
import dev.iwantquests.quest.QuestPack.Chapter;
import dev.iwantquests.quest.QuestPack.Objective;
import dev.iwantquests.quest.QuestPack.Quest;
import dev.iwantquests.quest.QuestPack.Reward;
import dev.iwantquests.reward.RewardDefinitionRegistry;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The editor's side panel. It shows the selected quest, the selected quests, or the current chapter, as text boxes
 * and buttons. Every box applies when the player presses Enter or clicks away; each change becomes one request.
 * <p>
 * Draft text and keyboard focus survive rebuilds for the same selection. Selection changes apply the old fields
 * first, while ordinary progress updates and window resizing leave unfinished text alone.
 */
final class QuestInspector {
    private static final int MAX_WIDTH = 240,
            MIN_WIDTH = 160,
            MARGIN = 8,
            HEADER = 50,
            FOOTER = 52,
            FIELD = 18,
            SMALL = 14;
    private static final int LINE = 10, GAP = 4, SCROLLBAR = 4, DESCRIPTION_HEIGHT = 44, HELD_WIDTH = 40;

    /** A text box with enough information to apply, or throw away, what the player typed into it. */
    private record Field(
            String key,
            Supplier<String> value,
            BooleanSupplier focused,
            Consumer<String> restore,
            Runnable focus,
            Consumer<String> apply,
            Runnable revert,
            String original,
            String[] submitted) {}

    private record Draft(String text, boolean changed, boolean focused, boolean rejected) {}

    private final QuestBookScreen screen;
    private final QuestEditing editing;
    private final QuestText qt;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final List<Field> fields = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> drawn = new ArrayList<>();
    private int scroll, cursor, content;
    private boolean rebuildNeeded;
    private String context = "";
    private int tab;

    QuestInspector(QuestBookScreen screen, QuestEditing editing) {
        this.screen = screen;
        this.editing = editing;
        this.qt = screen.qt;
    }

    static int width(int screenWidth) {
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, screenWidth / 4));
    }

    // ---------------------------------------------------------------- geometry

    private int panelX() {
        return screen.inspectorX();
    }

    private int panelWidth() {
        return width(screen.width);
    }

    private int left() {
        return panelX() + MARGIN;
    }

    private int innerWidth() {
        return panelWidth() - MARGIN * 2 - SCROLLBAR;
    }

    private int headerHeight() {
        Quest selected = editing.single();
        return selected != null && editing.editable(selected) ? HEADER : 26;
    }

    private int viewTop() {
        return screen.treeTop() + headerHeight();
    }

    private int viewBottom() {
        return screen.height - FOOTER - GAP;
    }

    private int ay(int rel) {
        return viewTop() + rel - scroll;
    }

    private boolean fits(int y, int h) {
        return y >= viewTop() && y + h <= viewBottom();
    }

    private QuestTheme theme() {
        return screen.theme();
    }

    boolean contains(double mx, double my) {
        return mx >= panelX() && mx < panelX() + panelWidth() && my >= screen.treeTop();
    }

    void resetScroll() {
        scroll = 0;
    }

    boolean hasDrafts() {
        return fields.stream().anyMatch(f -> !f.value().get().equals(f.submitted()[0]));
    }

    private void selectTab(int next) {
        if (tab == next) return;
        commitAll();
        tab = next;
        scroll = 0;
        screen.refreshWidgets();
    }

    void scroll(int delta) {
        int max = Math.max(0, content - (viewBottom() - viewTop()));
        int next = Math.clamp(scroll + delta, 0, max);
        if (next == scroll) return;
        commitAll();
        scroll = next;
        screen.refreshWidgets();
    }

    // ---------------------------------------------------------------- text boxes

    /** Applies every box whose text differs from the definition. Called before anything that rebuilds the panel. */
    void commitAll() {
        for (Field f : List.copyOf(fields)) {
            String text = f.value().get();
            if (!text.equals(f.submitted()[0])) {
                f.submitted()[0] = text;
                f.apply().accept(text);
            }
        }
    }

    /** Discards what is typed in the focused box. */
    void revert() {
        for (Field f : fields) if (f.focused().getAsBoolean()) f.revert().run();
        screen.setFocused(null);
    }

    void tick() {
        for (Field f : List.copyOf(fields)) {
            String text = f.value().get();
            if (!f.focused().getAsBoolean() && !text.equals(f.submitted()[0])) {
                f.submitted()[0] = text;
                f.apply().accept(text);
            }
        }
        if (rebuildNeeded) {
            rebuildNeeded = false;
            screen.refreshWidgets();
        }
    }

    private EditBox box(String key, int x, int rel, int w, String value, int max, String hint, Consumer<String> apply) {
        int y = ay(rel);
        EditBox box = new EditBox(minecraft.font, x, y, w, FIELD, Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        box.visible = fits(y, FIELD);
        screen.addEditorWidget(box);
        fields.add(new Field(
                key,
                box::getValue,
                box::isFocused,
                box::setValue,
                () -> {
                    if (box.visible) screen.setFocused(box);
                },
                apply,
                () -> box.setValue(value),
                value,
                new String[] {value}));
        return box;
    }

    private void numberBox(String key, int x, int rel, int w, int value, int min, int max, Consumer<Integer> apply) {
        EditBox box =
                box(key, x, rel, w, String.valueOf(value), String.valueOf(max).length(), "#", text -> {
                    try {
                        int parsed = Integer.parseInt(text.strip());
                        if (parsed != value) apply.accept(Math.clamp(parsed, min, max));
                    } catch (NumberFormatException ignored) {
                        screen.refreshWidgets();
                    }
                });
        box.setResponder(text -> {
            if (!text.matches("[0-9]*")) box.setValue(text.replaceAll("[^0-9]", ""));
        });
    }

    private void area(String key, int rel, String value, int max, String hint, Consumer<String> apply) {
        int y = ay(rel);
        MultiLineEditBox box = MultiLineEditBox.builder()
                .setPlaceholder(Component.literal(hint))
                .build(minecraft.font, innerWidth(), DESCRIPTION_HEIGHT, Component.literal(hint));
        box.setX(left());
        box.setY(y);
        box.setCharacterLimit(max);
        box.setValue(value);
        box.visible = fits(y, DESCRIPTION_HEIGHT);
        screen.addEditorWidget(box);
        fields.add(new Field(
                key,
                box::getValue,
                box::isFocused,
                box::setValue,
                () -> {
                    if (box.visible) screen.setFocused(box);
                },
                apply,
                () -> box.setValue(value),
                value,
                new String[] {value}));
    }

    private QuestButton button(int x, int rel, int w, int h, String label, @Nullable String tip, Button.OnPress press) {
        int y = ay(rel);
        QuestButton button = screen.addEditorWidget(new QuestButton(screen::theme, x, y, w, h, label, b -> {
            // A change still on its way to the server may have moved list positions the buttons were built for.
            boolean busy = ClientQuestState.editsInFlight > 0;
            commitAll();
            if (!busy) press.onPress(b);
        }));
        button.visible = fits(y, h);
        if (tip != null) button.setTooltip(Tooltip.create(Component.literal(QuestText.t(tip))));
        return button;
    }

    // ---------------------------------------------------------------- content builders

    private void text(String text, int rel, int x, int colour) {
        drawn.add(g -> qt.draw(g, text, x, ay(rel), colour));
    }

    private void heading(String text) {
        int rel = cursor;
        drawn.add(g -> qt.draw(g, text, left(), ay(rel), theme().heading));
        cursor += 13;
    }

    private void label(String text) {
        int rel = cursor;
        drawn.add(g -> qt.draw(g, text, left(), ay(rel), theme().subtle));
        cursor += LINE;
    }

    private void note(String text, boolean warning) {
        int rel = cursor;
        List<net.minecraft.util.FormattedCharSequence> lines = qt.wrap(text, innerWidth());
        drawn.add(g -> {
            int y = ay(rel);
            for (var line : lines) {
                qt.drawLine(g, line, left(), y, warning ? theme().locked : theme().muted);
                y += LINE;
            }
        });
        cursor += lines.size() * LINE + GAP;
    }

    private void divider() {
        int rel = cursor + 2;
        drawn.add(g -> g.fill(left(), ay(rel), left() + innerWidth(), ay(rel) + 1, theme().border));
        cursor += 8;
    }

    /** A section title with small buttons at its right edge, which stay inside the panel. */
    private void sectionRow(String title, String[] labels, String[] tips, Runnable[] actions) {
        cursor += 2;
        int rel = cursor;
        heading(title);
        int x = left() + innerWidth();
        for (int i = labels.length - 1; i >= 0; i--) {
            Runnable action = actions[i];
            int w = qt.width(labels[i]) + 10;
            x -= w;
            button(x, rel - 2, w, SMALL + 2, labels[i], tips[i], b -> action.run());
            x -= GAP;
        }
    }

    private void textField(String key, String value, int max, Consumer<String> apply) {
        label(QuestText.t(key));
        box(key, left(), cursor, innerWidth(), value, max, QuestText.t(key), apply);
        cursor += FIELD + GAP + 2;
    }

    private void iconRow(String key, @Nullable String value, Consumer<String> apply) {
        label(QuestText.t(key));
        int boxX = left() + 20, boxWidth = innerWidth() - 20 - GAP - HELD_WIDTH;
        int rel = cursor;
        String shown = value == null ? "" : value;
        box(key, boxX, rel, boxWidth, shown, 256, "minecraft:stone", apply);
        drawn.add(g -> {
            ItemStack stack = screen.icon(shown);
            if (!stack.isEmpty()) g.item(stack, left(), ay(rel));
        });
        button(boxX + boxWidth + GAP, rel, HELD_WIDTH, FIELD, QuestText.t("editor.held"), "editor.tip.held", b -> {
            String held = heldItem();
            if (held == null) editing.say(QuestText.t("editor.hold_item"));
            else apply.accept(held);
        });
        cursor += FIELD + GAP + 2;
    }

    private @Nullable String heldItem() {
        var player = minecraft.player;
        if (player == null || player.getMainHandItem().isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
    }

    private void browse(boolean tags, Consumer<String> apply) {
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("picker.browse"),
                "picker.open",
                b -> minecraft.gui.setScreen(new ItemPickerScreen(screen, tags, apply)));
        cursor += FIELD + GAP;
    }

    private void selectionTools() {
        int half = (innerWidth() - GAP) / 2;
        button(
                left(),
                cursor,
                half,
                FIELD,
                QuestText.t("editor.duplicate"),
                "editor.tip.duplicate",
                b -> editing.duplicate());
        button(
                left() + half + GAP,
                cursor,
                half,
                FIELD,
                QuestText.t("templates.save"),
                "templates.tip.save",
                b -> editing.saveTemplate());
        cursor += FIELD + GAP;
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("templates.title"),
                null,
                b -> minecraft.gui.setScreen(new TemplateScreen(screen)));
        cursor += FIELD + GAP;
    }

    // ---------------------------------------------------------------- panel

    /** Rebuilds the widgets for the current selection. Called by the screen whenever its widgets are rebuilt. */
    void build() {
        String nextContext =
                editing.selection().isEmpty() ? "chapter:" + screen.chapter : "quests:" + editing.selection();
        Map<String, Draft> drafts = new HashMap<>();
        if (context.equals(nextContext)) {
            for (Field field : fields) {
                String value = field.value().get();
                boolean rejected =
                        editing.failed() && value.equals(field.submitted()[0]) && !value.equals(field.original());
                drafts.put(
                        field.key(),
                        new Draft(
                                value,
                                rejected || !value.equals(field.submitted()[0]),
                                field.focused().getAsBoolean(),
                                rejected));
            }
        }
        if (!context.equals(nextContext)) {
            tab = 0;
            scroll = 0;
        }
        context = nextContext;
        screen.setFocused(null);
        fields.clear();
        drawn.clear();
        cursor = 0;
        int max = Math.max(0, content - (viewBottom() - viewTop()));
        scroll = Math.clamp(scroll, 0, max);
        List<Quest> picked = editing.selected();
        addNavigation(picked.size() == 1 && editing.editable(picked.getFirst()));
        if (picked.isEmpty()) chapterSection();
        else if (picked.size() == 1) questSection(picked.getFirst());
        else multiSection(picked);
        for (Field field : fields) {
            Draft draft = drafts.get(field.key());
            if (draft == null) continue;
            if (draft.changed()) {
                field.restore().accept(draft.text());
                // Keep a rejected value available for correction without resending it on every tick.
                if (draft.rejected()) field.submitted()[0] = draft.text();
            }
            if (draft.focused()) field.focus().run();
        }
        int previous = content;
        content = cursor;
        if (previous != content && scroll > Math.max(0, content - (viewBottom() - viewTop()))) rebuildNeeded = true;
    }

    private JsonObject set(String field, @Nullable Object value) {
        JsonObject object = new JsonObject();
        object.add(field, value == null ? JsonNull.INSTANCE : QuestLoader.JSON.toJsonTree(value));
        return object;
    }

    private static String nz(@Nullable String text) {
        return text == null ? "" : text;
    }

    private void addNavigation(boolean quest) {
        if (quest) {
            String[] labels = {
                QuestText.t("editor.tab.details"), QuestText.t("editor.tasks"), QuestText.t("editor.rewards")
            };
            int w = (innerWidth() - GAP * 2) / 3;
            for (int i = 0; i < labels.length; i++) {
                int index = i;
                QuestButton button = screen.addEditorWidget(new QuestButton(
                        screen::theme,
                        left() + i * (w + GAP),
                        screen.treeTop() + 27,
                        w,
                        FIELD,
                        labels[i],
                        b -> selectTab(index)));
                button.primary = tab == i;
            }
        }
        int third = (innerWidth() - GAP * 2) / 3, y = screen.height - 27;
        QuestButton save = screen.addEditorWidget(new QuestButton(
                screen::theme, left(), y, third, FIELD, QuestText.t("editor.save"), b -> editing.savePending()));
        save.setTooltip(Tooltip.create(Component.literal(QuestText.t("editor.tip.save"))));
        screen.addEditorWidget(new QuestButton(
                screen::theme, left() + third + GAP, y, third, FIELD, QuestText.t("draft.preview"), b -> {
                    editing.savePending();
                    minecraft.gui.setScreen(new QuestBookScreen(screen));
                }));
        screen.addEditorWidget(new QuestButton(
                screen::theme, left() + 2 * (third + GAP), y, third, FIELD, QuestText.t("draft.review"), b -> {
                    editing.savePending();
                    minecraft.gui.setScreen(new PublishScreen(screen));
                }));
    }

    private void questSection(Quest q) {
        boolean mine = editing.editable(q);
        if (!mine) {
            note(QuestText.t("editor.readonly"), true);
            note(q.title(), false);
            return;
        }
        if (tab == 1) {
            tasks(q);
            return;
        }
        if (tab == 2) {
            rewards(q);
            return;
        }
        textField("editor.field.title", q.title(), 100, v -> {
            if (v.isBlank()) screen.refreshWidgets();
            else editing.patchQuest(q.id(), set("title", v.strip()));
        });
        textField(
                "editor.field.subtitle",
                nz(q.subtitle()),
                128,
                v -> editing.patchQuest(q.id(), set("subtitle", v.isBlank() ? null : v.strip())));
        label(QuestText.t("editor.field.description"));
        area(
                "description",
                cursor,
                nz(q.description()),
                1000,
                QuestText.t("editor.field.description"),
                v -> editing.patchQuest(q.id(), set("description", v.isBlank() ? null : v)));
        cursor += DESCRIPTION_HEIGHT + GAP + 2;
        iconRow(
                "editor.field.icon",
                q.icon(),
                v -> editing.patchQuest(q.id(), set("icon", v.isBlank() ? null : v.strip())));
        browse(false, v -> editing.patchQuest(q.id(), set("icon", v)));
        shapeAndSize(q);
        flags(q);
        divider();
        prerequisites(q);
        divider();
        selectionTools();
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("editor.delete_quest"),
                null,
                b -> editing.deleteSelection());
        cursor += FIELD + MARGIN;
    }

    private void shapeAndSize(Quest q) {
        List<String> shapes = new ArrayList<>(List.of("task", "goal", "challenge"));
        theme().shapes.keySet().stream()
                .sorted()
                .filter(s -> !shapes.contains(s))
                .forEach(shapes::add);
        String current = q.shapeOrDefault();
        if (!shapes.contains(current)) shapes.add(current);
        int half = (innerWidth() - GAP) / 2;
        label(QuestText.t("editor.field.shape") + " / " + QuestText.t("editor.field.size"));
        button(left(), cursor, half, FIELD, QuestViews.shapeLabel(current), "editor.tip.shape", b -> {
            String next = shapes.get((shapes.indexOf(current) + 1) % shapes.size());
            editing.patchQuest(q.id(), set("shape", next.equals("task") ? null : next));
        });
        double size = q.sizeOrDefault();
        int stepper = FIELD, middle = half - stepper * 2 - GAP * 2;
        int x = left() + half + GAP;
        button(
                x,
                cursor,
                stepper,
                FIELD,
                QuestText.t("zoom_out"),
                null,
                b -> editing.patchQuest(q.id(), set("size", Math.max(0.5, size - 0.25))));
        int sizeRel = cursor;
        String sizeText = trim(size);
        drawn.add(g -> qt.drawCentered(g, sizeText, x + stepper + GAP + middle / 2, ay(sizeRel) + 4, theme().text));
        button(
                x + stepper + GAP + middle + GAP,
                cursor,
                stepper,
                FIELD,
                QuestText.t("zoom_in"),
                null,
                b -> editing.patchQuest(q.id(), set("size", Math.min(3.0, size + 0.25))));
        cursor += FIELD + GAP + 2;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private void flags(Quest q) {
        String[] names = {"editor.optional", "editor.hidden", "editor.retroactive", "editor.repeatable"};
        String[] fieldNames = {"optional", "hidden", "retroactive", "repeatable"};
        boolean[] values = {q.optional(), q.hidden(), q.retroactive(), q.repeatable()};
        for (int i = 0; i < names.length; i++) {
            int index = i;
            QuestButton toggle = button(
                    left(),
                    cursor,
                    innerWidth(),
                    FIELD,
                    QuestText.t(names[i]) + ": " + QuestText.t(values[i] ? "editor.on" : "editor.off"),
                    names[i] + "_tip",
                    b -> editing.patchQuest(q.id(), set(fieldNames[index], !values[index])));
            toggle.primary = values[i];
            cursor += FIELD + GAP;
        }
    }

    private void prerequisites(Quest q) {
        heading(QuestText.t("editor.prerequisites"));
        List<String> prerequisites = QuestViews.list(q.prerequisites());
        if (prerequisites.isEmpty()) {
            note(QuestText.t("editor.prerequisites_none"), false);
            return;
        }
        for (String id : prerequisites) {
            Quest parent = screen.findQuest(id);
            String title = qt.clip(parent == null ? id : parent.title(), innerWidth() - SMALL - GAP * 2);
            int rel = cursor;
            drawn.add(g -> qt.draw(g, title, left(), ay(rel) + 3, theme().text));
            button(
                    left() + innerWidth() - SMALL,
                    rel,
                    SMALL,
                    SMALL,
                    QuestText.t("editor.remove_glyph"),
                    "editor.tip.remove",
                    b -> {
                        List<String> rest = new ArrayList<>(prerequisites);
                        rest.remove(id);
                        editing.patchQuest(q.id(), set("prerequisites", rest));
                    });
            cursor += SMALL + 2;
        }
    }

    // ---------------------------------------------------------------- tasks

    private void tasks(Quest q) {
        // One list shared by every box of this quest, so several edits applied together do not overwrite each other.
        List<Objective> objectives = new ArrayList<>(QuestViews.list(q.objectives()));
        sectionRow(
                QuestText.t("editor.tasks"),
                new String[] {QuestText.t("editor.add_task")},
                new String[] {"editor.tip.add_task"},
                new Runnable[] {() -> addTask(q, objectives)});
        for (int i = 0; i < objectives.size(); i++) task(q, objectives, i);
    }

    private void addTask(Quest q, List<Objective> objectives) {
        Set<String> ids = new HashSet<>();
        objectives.forEach(o -> ids.add(o.id()));
        int n = objectives.size() + 1;
        while (ids.contains("task_" + n)) n++;
        objectives.add(Objective.simple("task_" + n, "obtain", "minecraft:stone", 1, ""));
        editing.patchQuest(q.id(), set("objectives", new ArrayList<>(objectives)));
    }

    private void change(Quest q, List<Objective> objectives, int index, UnaryOperator<Objective> change) {
        objectives.set(index, change.apply(objectives.get(index)));
        editing.patchQuest(q.id(), set("objectives", new ArrayList<>(objectives)));
    }

    private static Objective copy(
            Objective o, String type, @Nullable String target, int count, @Nullable String description) {
        return new Objective(
                o.id(),
                type,
                target,
                count,
                description,
                o.dimension(),
                o.minX(),
                o.minY(),
                o.minZ(),
                o.maxX(),
                o.maxY(),
                o.maxZ());
    }

    private void task(Quest q, List<Objective> objectives, int index) {
        Objective o = objectives.get(index);
        int countWidth = 46, removeWidth = SMALL + 2;
        int typeWidth = innerWidth() - countWidth - removeWidth - GAP * 2;
        List<String> types = new ArrayList<>(ObjectiveRegistry.types());
        if (!types.contains(o.type())) types.add(o.type());
        button(
                left(),
                cursor,
                typeWidth,
                FIELD,
                typeLabel("editor.type.", o.type()),
                "editor.tip.task_type",
                b -> change(
                        q,
                        objectives,
                        index,
                        old -> retyped(old, types.get((types.indexOf(old.type()) + 1) % types.size()))));
        numberBox(
                "task:" + o.id() + ":count",
                left() + typeWidth + GAP,
                cursor,
                countWidth,
                Math.max(1, o.count()),
                1,
                1_000_000,
                v -> change(q, objectives, index, old -> copy(old, old.type(), old.target(), v, old.description())));
        button(
                left() + typeWidth + countWidth + GAP * 2,
                cursor,
                removeWidth,
                FIELD,
                QuestText.t("editor.remove_glyph"),
                "editor.tip.remove_task",
                b -> {
                    if (objectives.size() <= 1) {
                        editing.say(QuestText.t("editor.last_task"));
                        return;
                    }
                    objectives.remove(index);
                    editing.patchQuest(q.id(), set("objectives", new ArrayList<>(objectives)));
                });
        cursor += FIELD + GAP;
        if (hasTarget(o.type())) {
            Consumer<String> apply = v -> change(
                    q,
                    objectives,
                    index,
                    old -> copy(old, old.type(), v.isBlank() ? null : v.strip(), old.count(), old.description()));
            int pickerWidth = isItemType(o.type()) ? 28 : 0;
            box(
                    "task:" + o.id() + ":target",
                    left(),
                    cursor,
                    innerWidth() - pickerWidth,
                    nz(o.target()),
                    256,
                    targetHint(o.type()),
                    apply);
            if (pickerWidth > 0)
                button(
                        left() + innerWidth() - 24,
                        cursor,
                        24,
                        FIELD,
                        "...",
                        "picker.open",
                        b -> minecraft.gui.setScreen(new ItemPickerScreen(screen, true, apply)));
            cursor += FIELD + GAP;
        } else if (o.type().equals("region")) {
            region(q, objectives, index, o);
        }
        box(
                "task:" + o.id() + ":description",
                left(),
                cursor,
                innerWidth(),
                nz(o.description()),
                200,
                QuestText.t("editor.task_description"),
                v -> change(
                        q,
                        objectives,
                        index,
                        old -> copy(old, old.type(), old.target(), old.count(), v.isBlank() ? null : v)));
        cursor += FIELD + GAP + 4;
    }

    private void region(Quest q, List<Objective> objectives, int index, Objective o) {
        String summary = QuestText.t(
                "editor.region_summary",
                nz(o.dimension()),
                Math.round(o.minX()),
                Math.round(o.minY()),
                Math.round(o.minZ()),
                Math.round(o.maxX()),
                Math.round(o.maxY()),
                Math.round(o.maxZ()));
        note(summary, false);
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("editor.set_here"),
                "editor.tip.set_here",
                b -> change(q, objectives, index, old -> aroundPlayer(old)));
        cursor += FIELD + GAP;
    }

    private Objective aroundPlayer(Objective o) {
        var player = minecraft.player;
        if (player == null) return o;
        String dimension = player.level().dimension().identifier().toString();
        double x = Math.floor(player.getX()), y = Math.floor(player.getY()), z = Math.floor(player.getZ());
        return new Objective(
                o.id(),
                "region",
                null,
                o.count(),
                o.description(),
                dimension,
                x - 8,
                y - 4,
                z - 8,
                x + 8,
                y + 4,
                z + 8);
    }

    /** Switching type resets what the old target meant, so the objective stays valid. */
    private Objective retyped(Objective o, String type) {
        String target = o.target();
        boolean itemLike = isItemType(o.type());
        switch (type) {
            case "kill" -> target = "minecraft:zombie";
            case "dimension" -> target = "minecraft:the_nether";
            case "manual", "region" -> target = null;
            case "obtain", "possess", "craft", "submit" -> {
                if (!itemLike || target == null) target = "minecraft:stone";
            }
            default -> {}
        }
        Objective retyped = copy(o, type, target, o.count(), o.description());
        if (type.equals("region")) return aroundPlayer(retyped);
        return new Objective(o.id(), type, target, o.count(), o.description(), null, 0, 0, 0, 0, 0, 0);
    }

    private static boolean isItemType(String type) {
        return List.of("obtain", "possess", "craft", "submit").contains(type);
    }

    private static boolean hasTarget(String type) {
        return !type.equals("manual") && !type.equals("region");
    }

    private static String targetHint(String type) {
        return switch (type) {
            case "kill" -> QuestText.t("editor.target_entity");
            case "dimension" -> QuestText.t("editor.target_dimension");
            default -> QuestText.t("editor.target_item");
        };
    }

    private static String typeLabel(String prefix, String type) {
        String key = prefix + type;
        if (Language.getInstance().has("iwantquests.ui." + key)) return QuestText.t(key);
        return type.isEmpty() ? type : Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    // ---------------------------------------------------------------- rewards

    private void rewards(Quest q) {
        List<Reward> rewards = new ArrayList<>(QuestViews.list(q.rewards()));
        sectionRow(
                QuestText.t("editor.rewards"),
                new String[] {
                    QuestText.t("editor.add_item"),
                    QuestText.t("editor.add_xp"),
                    QuestText.t("editor.add_command"),
                    QuestText.t("editor.add_loot")
                },
                new String[] {
                    "editor.tip.add_item", "editor.tip.add_xp", "editor.tip.add_command", "editor.tip.add_loot"
                },
                new Runnable[] {
                    () -> addReward(q, rewards, new Reward("item", defaultTarget("item"), 1)),
                    () -> addReward(q, rewards, new Reward("xp", null, 10)),
                    () -> addReward(q, rewards, new Reward("command", defaultTarget("command"), 1)),
                    () -> addReward(q, rewards, new Reward("loot_table", defaultTarget("loot_table"), 1))
                });
        if (rewards.isEmpty()) note(QuestText.t("editor.no_rewards"), false);
        for (int i = 0; i < rewards.size(); i++) reward(q, rewards, i);
    }

    private static String defaultTarget(String type) {
        return switch (type) {
            case "item" -> "minecraft:bread";
            case "command" -> "say {player} finished a quest";
            case "loot_table" -> "minecraft:chests/simple_dungeon";
            default -> null;
        };
    }

    private static int maxCount(String type) {
        return switch (type) {
            case "command" -> 100;
            case "loot_table" -> 64;
            case "random" -> 16;
            default -> 1_000_000;
        };
    }

    private void addReward(Quest q, List<Reward> rewards, Reward added) {
        rewards.add(added);
        editing.patchQuest(q.id(), set("rewards", new ArrayList<>(rewards)));
    }

    private void changeReward(Quest q, List<Reward> rewards, int index, UnaryOperator<Reward> change) {
        rewards.set(index, change.apply(rewards.get(index)));
        editing.patchQuest(q.id(), set("rewards", new ArrayList<>(rewards)));
    }

    private void reward(Quest q, List<Reward> rewards, int index) {
        Reward r = rewards.get(index);
        int countWidth = 56, removeWidth = SMALL + 2;
        int typeWidth = innerWidth() - countWidth - removeWidth - GAP * 2;
        // Random and choice rewards hold other rewards, which the editor leaves untouched; they are authored in JSON.
        boolean container = RewardDefinitionRegistry.CONTAINERS.contains(r.type());
        List<String> types = new ArrayList<>(RewardDefinitionRegistry.types());
        types.removeAll(RewardDefinitionRegistry.CONTAINERS);
        if (!types.contains(r.type()) && !container) types.add(r.type());
        QuestButton typeButton = button(
                left(),
                cursor,
                typeWidth,
                FIELD,
                typeLabel("editor.reward.", r.type()),
                "editor.tip.reward_type",
                b -> {
                    String next = types.get((types.indexOf(r.type()) + 1) % types.size());
                    changeReward(
                            q,
                            rewards,
                            index,
                            old -> old.withType(next)
                                    .withTarget(defaultTarget(next))
                                    .withCount(Math.min(maxCount(next), Math.max(1, old.count()))));
                });
        typeButton.active = !container;
        if (!r.type().equals("choice"))
            numberBox(
                    "reward:" + index + ":count",
                    left() + typeWidth + GAP,
                    cursor,
                    countWidth,
                    Math.max(1, r.count()),
                    1,
                    maxCount(r.type()),
                    v -> changeReward(q, rewards, index, old -> old.withCount(v)));
        button(
                left() + typeWidth + countWidth + GAP * 2,
                cursor,
                removeWidth,
                FIELD,
                QuestText.t("editor.remove_glyph"),
                "editor.tip.remove_reward",
                b -> {
                    rewards.remove(index);
                    editing.patchQuest(q.id(), set("rewards", new ArrayList<>(rewards)));
                });
        cursor += FIELD + GAP;
        if (r.type().equals("item")) {
            int boxX = left() + 20, boxWidth = innerWidth() - 20 - GAP - HELD_WIDTH;
            int rel = cursor;
            String target = nz(r.target());
            box(
                    "reward:" + index + ":target",
                    boxX,
                    rel,
                    boxWidth,
                    target,
                    256,
                    "minecraft:bread",
                    v -> changeReward(q, rewards, index, old -> old.withTarget(v.strip())));
            drawn.add(g -> {
                ItemStack stack = screen.icon(target);
                if (!stack.isEmpty()) g.item(stack, left(), ay(rel));
            });
            button(boxX + boxWidth + GAP, rel, HELD_WIDTH, FIELD, QuestText.t("editor.held"), "editor.tip.held", b -> {
                String held = heldItem();
                if (held == null) editing.say(QuestText.t("editor.hold_item"));
                else changeReward(q, rewards, index, old -> old.withTarget(held));
            });
            cursor += FIELD + GAP;
            browse(false, v -> changeReward(q, rewards, index, old -> old.withTarget(v)));
        } else if (r.type().equals("command") || r.type().equals("loot_table")) {
            boolean command = r.type().equals("command");
            box(
                    "reward:" + index + ":target",
                    left(),
                    cursor,
                    innerWidth(),
                    nz(r.target()),
                    command ? 512 : 256,
                    QuestText.t(command ? "editor.target_command" : "editor.target_loot"),
                    v -> changeReward(q, rewards, index, old -> old.withTarget(v.strip())));
            cursor += FIELD + GAP;
        } else if (container) {
            note(QuestText.t("editor.reward_options_json", r.optionsOrEmpty().size()), false);
        }
        cursor += 4;
    }

    // ---------------------------------------------------------------- several quests and chapters

    private void multiSection(List<Quest> picked) {
        note(QuestText.t("editor.selected", picked.size()), false);
        boolean allEditable = picked.stream().allMatch(editing::editable);
        if (!allEditable) note(QuestText.t("editor.readonly_some"), true);
        int half = (innerWidth() - GAP) / 2;
        QuestButton alignX = button(
                left(),
                cursor,
                half,
                FIELD,
                QuestText.t("editor.align_x"),
                "editor.tip.align_x",
                b -> align(picked, true));
        QuestButton alignY = button(
                left() + half + GAP,
                cursor,
                half,
                FIELD,
                QuestText.t("editor.align_y"),
                "editor.tip.align_y",
                b -> align(picked, false));
        alignX.active = alignY.active = allEditable;
        cursor += FIELD + GAP + 2;
        selectionTools();
        QuestButton delete = button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("editor.delete_quests", picked.size()),
                null,
                b -> editing.deleteSelection());
        delete.active = allEditable;
        cursor += FIELD + MARGIN;
    }

    /** Moves every selected quest onto the first one's column (or row). */
    private void align(List<Quest> picked, boolean column) {
        double target = column ? editing.x(picked.getFirst()) : editing.y(picked.getFirst());
        Map<String, JsonObject> sets = new LinkedHashMap<>();
        for (Quest q : picked) {
            double current = column ? editing.x(q) : editing.y(q);
            if (current != target) sets.put(q.id(), set(column ? "x" : "y", target));
        }
        if (!sets.isEmpty()) editing.patchQuests(sets);
    }

    private void chapterSection() {
        Chapter c = screen.currentChapter();
        if (c == null) {
            note(QuestText.t("editor.no_chapters"), false);
            button(
                    left(),
                    cursor,
                    innerWidth(),
                    FIELD,
                    QuestText.t("editor.new_chapter"),
                    null,
                    b -> editing.createChapter());
            cursor += FIELD + MARGIN;
            return;
        }
        if (!editing.editable(c)) {
            note(QuestText.t("editor.readonly_chapter"), true);
            note(c.title(), false);
            newChapterButton();
            return;
        }
        textField("editor.field.title", c.title(), 100, v -> {
            if (v.isBlank()) screen.refreshWidgets();
            else editing.patchChapter(c.id(), set("title", v.strip()));
        });
        textField(
                "editor.field.subtitle",
                nz(c.subtitle()),
                128,
                v -> editing.patchChapter(c.id(), set("subtitle", v.isBlank() ? null : v.strip())));
        iconRow("editor.field.icon", c.icon(), v -> editing.patchChapter(c.id(), set("icon", v.strip())));
        browse(false, v -> editing.patchChapter(c.id(), set("icon", v)));
        label(QuestText.t("editor.field.order"));
        int stepper = FIELD, middle = 40;
        button(
                left(),
                cursor,
                stepper,
                FIELD,
                QuestText.t("zoom_out"),
                null,
                b -> editing.patchChapter(c.id(), set("order", c.order() - 1)));
        int orderRel = cursor;
        drawn.add(g -> qt.drawCentered(
                g, String.valueOf(c.order()), left() + stepper + GAP + middle / 2, ay(orderRel) + 4, theme().text));
        button(
                left() + stepper + GAP + middle + GAP,
                cursor,
                stepper,
                FIELD,
                QuestText.t("zoom_in"),
                null,
                b -> editing.patchChapter(c.id(), set("order", c.order() + 1)));
        cursor += FIELD + GAP + 2;
        textField(
                "editor.field.theme",
                nz(c.theme()),
                256,
                v -> editing.patchChapter(c.id(), set("theme", v.isBlank() ? null : v.strip())));
        textField(
                "editor.field.background",
                nz(c.background()),
                256,
                v -> editing.patchChapter(c.id(), set("background", v.isBlank() ? null : v.strip())));
        long count =
                screen.quests().stream().filter(q -> c.id().equals(q.chapter())).count();
        note(QuestText.t(count == 1 ? "editor.chapter_quests.one" : "editor.chapter_quests.many", count), false);
        divider();
        newChapterButton();
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("editor.delete_chapter"),
                null,
                b -> confirmDelete(c, count));
        cursor += FIELD + MARGIN;
    }

    private void newChapterButton() {
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("templates.title"),
                null,
                b -> minecraft.gui.setScreen(new TemplateScreen(screen)));
        cursor += FIELD + GAP;
        button(
                left(),
                cursor,
                innerWidth(),
                FIELD,
                QuestText.t("editor.new_chapter"),
                null,
                b -> editing.createChapter());
        cursor += FIELD + GAP + 2;
    }

    private void confirmDelete(Chapter c, long quests) {
        minecraft.gui.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) editing.deleteChapter(c);
                    minecraft.gui.setScreen(screen);
                },
                Component.translatable("iwantquests.ui.editor.confirm_delete_chapter_title"),
                Component.translatable("iwantquests.ui.editor.confirm_delete_chapter", c.title(), quests)));
    }

    // ---------------------------------------------------------------- rendering

    void draw(GuiGraphicsExtractor g, QuestTheme t, int mouseX, int mouseY) {
        int x = panelX(), top = screen.treeTop(), w = panelWidth();
        QuestDraw.panel(g, t.panelSprite, x, top, w, screen.height - top, t.panel | 0xff000000, 0);
        int edge = x == 0 ? x + w - 1 : x;
        g.fill(edge, top, edge + 1, screen.height, t.border);
        List<Quest> picked = editing.selected();
        String title;
        String subtitle;
        if (picked.isEmpty()) {
            Chapter c = screen.currentChapter();
            title = QuestText.t("editor.chapter");
            subtitle = c == null ? "" : c.id();
        } else if (picked.size() == 1) {
            title = QuestText.t("editor.quest");
            subtitle = picked.getFirst().id();
        } else {
            title = QuestText.t("editor.quests");
            subtitle = "";
        }
        qt.draw(g, title, left(), top + 5, t.text);
        qt.drawClipped(g, subtitle, left(), top + 15, innerWidth(), t.subtle);
        g.fill(x + MARGIN, top + headerHeight() - 2, x + w - MARGIN, top + headerHeight() - 1, t.border);
        g.enableScissor(x, viewTop(), x + w, viewBottom());
        for (Consumer<GuiGraphicsExtractor> draw : drawn) draw.accept(g);
        g.disableScissor();
        int footerY = screen.height - FOOTER;
        g.fill(left(), footerY, left() + innerWidth(), footerY + 1, t.border);
        String status = ClientQuestState.editsInFlight > 0
                ? QuestText.t("editor.saving")
                : hasDrafts()
                        ? QuestText.t("editor.unsaved")
                        : editing.failed() ? QuestText.t("editor.save_failed") : QuestText.t("editor.saved");
        qt.drawClipped(
                g, status, left(), footerY + 10, innerWidth(), hasDrafts() || editing.failed() ? t.locked : t.muted);
        int viewHeight = viewBottom() - viewTop();
        if (content > viewHeight) {
            int trackX = x + w - SCROLLBAR + 1;
            g.fill(trackX, viewTop(), trackX + 2, viewBottom(), t.progressBack);
            int thumb = Math.max(12, viewHeight * viewHeight / content);
            int thumbY = viewTop() + scroll * (viewHeight - thumb) / Math.max(1, content - viewHeight);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, t.subtle);
        }
    }
}
