package dev.iwantquests.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Visual settings for the quest book, loaded from {@code assets/<namespace>/iwantquests/themes/<name>.json}.
 * Every field is optional in JSON; omitted values come from the parent theme (by default {@link #DEFAULT}).
 */
public final class QuestTheme {
    /** Sprites for a node frame plus the flat geometry used when {@code flat_nodes} is on. */
    public record Shape(
            @Nullable Identifier complete, @Nullable Identifier incomplete, String geometry, float scale) {}

    /** Where a floating element sits. {@code HIDDEN} removes it. */
    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        LEFT,
        RIGHT,
        HIDDEN;

        static Anchor parse(String key, String value, Anchor fallback) {
            for (Anchor anchor : values()) if (anchor.name().equalsIgnoreCase(value)) return anchor;
            throw new IllegalArgumentException(
                    key + " must be one of " + Arrays.toString(values()).toLowerCase(Locale.ROOT));
        }
    }

    /** Geometries the flat node renderer knows. */
    public static final Set<String> GEOMETRIES = Set.of("circle", "square", "rounded", "octagon", "hexagon", "diamond");

    // Colours are ARGB.
    public final int text, muted, subtle, heading, complete, ready, inProgress, locked;
    public final int background, overlay, panel, header, sidebar, sidebarSelected, border, tab, tabSelected;
    public final int accent, card, buttonFill, buttonHoverFill, buttonDisabledFill;
    public final int lineComplete, lineAvailable, lineLocked, lineOutline;
    public final int progressFill, progressBack, selection, nodeFace, nodeFaceLocked;
    // GUI sprite IDs (assets/<ns>/textures/gui/sprites/<path>.png). Nullable ones fall back to flat colour drawing.
    public final Identifier slot, notification, button, buttonHighlighted, buttonDisabled;
    public final @Nullable Identifier backgroundTexture, tooltipStyle;
    public final @Nullable Identifier panelSprite, sidebarSprite, tabSprite, tabSelectedSprite, headerSprite;
    public final @Nullable Identifier badgeComplete, badgeLocked;
    public final @Nullable Identifier font;
    public final Map<String, Shape> shapes;
    // Layout in GUI pixels.
    public final int nodeSize,
            detailsWidth,
            sidebarWidth,
            sidebarExpandedWidth,
            headerHeight,
            lineWidth,
            nodeBorder,
            tabHeight;
    public final float nodeIconScale;
    public final Anchor sidebarPosition, titlePosition, controlsPosition, actionsPosition;
    public final boolean blur, textShadow, nodeProgressBars, canvasGrid, directConnections, flatButtons, flatNodes;
    public final boolean sidebarProgress, nodeTitles, animations, arrowheads, chapterTitle, lineOutlines;
    public final float lockedAlpha;

    public static final QuestTheme DEFAULT = new QuestTheme();

    private QuestTheme() {
        text = 0xffffffff;
        muted = 0xffaab4c0;
        subtle = 0xff7a8594;
        heading = 0xff7fd6c2;
        complete = 0xff5bd18b;
        ready = 0xffffb347;
        inProgress = 0xffe8d66b;
        locked = 0xffff6b6b;
        background = 0xd9111720;
        overlay = 0x99080b10;
        panel = 0xf21a212c;
        header = 0xf2161c26;
        sidebar = 0xe612171f;
        sidebarSelected = 0xff1d2633;
        border = 0xff2e3a4a;
        tab = 0x00000000;
        tabSelected = 0xff4fd1b5;
        lineComplete = 0xff5bd18b;
        lineAvailable = 0xff8fa3b8;
        lineLocked = 0xff39424f;
        lineOutline = 0x00000000;
        progressFill = 0xff4fd18b;
        progressBack = 0xff0b0f14;
        selection = 0xffffffff;
        accent = 0xff4fd1b5;
        card = 0xff202a37;
        buttonFill = 0xff2c3848;
        buttonHoverFill = 0xff3a4a5e;
        buttonDisabledFill = 0xff1d2531;
        nodeFace = 0xff1a222d;
        nodeFaceLocked = 0xff12171e;
        slot = mc("container/slot");
        notification = mc("icon/unseen_notification");
        button = mc("widget/button");
        buttonHighlighted = mc("widget/button_highlighted");
        buttonDisabled = mc("widget/button_disabled");
        backgroundTexture = null;
        tooltipStyle = null;
        panelSprite = null;
        sidebarSprite = null;
        tabSprite = null;
        tabSelectedSprite = null;
        headerSprite = null;
        badgeComplete = null;
        badgeLocked = null;
        font = null;
        shapes = Map.of(
                "task",
                        new Shape(
                                mc("advancements/task_frame_obtained"),
                                mc("advancements/task_frame_unobtained"),
                                "circle",
                                1f),
                "goal",
                        new Shape(
                                mc("advancements/goal_frame_obtained"),
                                mc("advancements/goal_frame_unobtained"),
                                "rounded",
                                1f),
                "challenge",
                        new Shape(
                                mc("advancements/challenge_frame_obtained"),
                                mc("advancements/challenge_frame_unobtained"),
                                "hexagon",
                                1f));
        nodeSize = 30;
        detailsWidth = 170;
        sidebarWidth = 30;
        sidebarExpandedWidth = 140;
        headerHeight = 0;
        lineWidth = 2;
        nodeBorder = 2;
        tabHeight = 24;
        nodeIconScale = .56f;
        sidebarPosition = Anchor.LEFT;
        titlePosition = Anchor.TOP_LEFT;
        controlsPosition = Anchor.BOTTOM_RIGHT;
        actionsPosition = Anchor.TOP_RIGHT;
        blur = true;
        textShadow = true;
        nodeProgressBars = true;
        canvasGrid = true;
        directConnections = true;
        flatButtons = true;
        flatNodes = true;
        sidebarProgress = true;
        nodeTitles = false;
        animations = true;
        arrowheads = false;
        chapterTitle = true;
        lineOutlines = false;
        lockedAlpha = .6f;
    }

    private QuestTheme(JsonObject json, QuestTheme p) {
        JsonObject c = object(json, "colors"),
                s = object(json, "sprites"),
                l = object(json, "layout"),
                o = object(json, "options");
        text = color(c, "text", p.text);
        muted = color(c, "muted", p.muted);
        subtle = color(c, "subtle", p.subtle);
        heading = color(c, "heading", p.heading);
        complete = color(c, "complete", p.complete);
        ready = color(c, "ready", p.ready);
        inProgress = color(c, "in_progress", p.inProgress);
        locked = color(c, "locked", p.locked);
        background = color(c, "background", p.background);
        overlay = color(c, "overlay", p.overlay);
        panel = color(c, "panel", p.panel);
        header = color(c, "header", p.header);
        sidebar = color(c, "sidebar", p.sidebar);
        sidebarSelected = color(c, "sidebar_selected", p.sidebarSelected);
        border = color(c, "border", p.border);
        tab = color(c, "tab", p.tab);
        tabSelected = color(c, "tab_selected", p.tabSelected);
        lineComplete = color(c, "line_complete", p.lineComplete);
        lineAvailable = color(c, "line_available", p.lineAvailable);
        lineLocked = color(c, "line_locked", p.lineLocked);
        lineOutline = color(c, "line_outline", p.lineOutline);
        progressFill = color(c, "progress_fill", p.progressFill);
        progressBack = color(c, "progress_back", p.progressBack);
        selection = color(c, "selection", p.selection);
        accent = color(c, "accent", p.accent);
        card = color(c, "card", p.card);
        buttonFill = color(c, "button", p.buttonFill);
        buttonHoverFill = color(c, "button_hover", p.buttonHoverFill);
        buttonDisabledFill = color(c, "button_disabled", p.buttonDisabledFill);
        nodeFace = color(c, "node_face", p.nodeFace);
        nodeFaceLocked = color(c, "node_face_locked", p.nodeFaceLocked);
        slot = id(s, "slot", p.slot);
        notification = id(s, "notification", p.notification);
        button = id(s, "button", p.button);
        buttonHighlighted = id(s, "button_highlighted", p.buttonHighlighted);
        buttonDisabled = id(s, "button_disabled", p.buttonDisabled);
        backgroundTexture = id(s, "background", p.backgroundTexture);
        tooltipStyle = id(s, "tooltip", p.tooltipStyle);
        panelSprite = id(s, "panel", p.panelSprite);
        sidebarSprite = id(s, "sidebar", p.sidebarSprite);
        tabSprite = id(s, "tab", p.tabSprite);
        tabSelectedSprite = id(s, "tab_selected", p.tabSelectedSprite);
        headerSprite = id(s, "header", p.headerSprite);
        badgeComplete = id(s, "badge_complete", p.badgeComplete);
        badgeLocked = id(s, "badge_locked", p.badgeLocked);
        font = id(o, "font", p.font);
        Map<String, Shape> merged = new HashMap<>(p.shapes);
        for (var e : object(json, "shapes").entrySet()) {
            JsonObject shape = e.getValue().getAsJsonObject();
            Shape base = merged.getOrDefault(e.getKey(), p.shapes.get("task"));
            Identifier complete = id(shape, "complete", base.complete());
            String geometry = shape.has("geometry")
                    ? shape.get("geometry").getAsString().toLowerCase(Locale.ROOT)
                    : base.geometry();
            if (!GEOMETRIES.contains(geometry))
                throw new IllegalArgumentException(
                        "Shape " + e.getKey() + " geometry must be one of " + new TreeSet<>(GEOMETRIES));
            float scale = shape.has("scale") ? Math.clamp(shape.get("scale").getAsFloat(), .25f, 3f) : base.scale();
            merged.put(
                    e.getKey(),
                    new Shape(
                            complete,
                            id(shape, "incomplete", shape.has("complete") ? complete : base.incomplete()),
                            geometry,
                            scale));
        }
        shapes = Map.copyOf(merged);
        nodeSize = range(l, "node_size", p.nodeSize, 8, 128);
        detailsWidth = range(l, "details_width", p.detailsWidth, 100, 400);
        sidebarWidth = range(l, "sidebar_width", p.sidebarWidth, 0, 128);
        sidebarExpandedWidth = range(l, "sidebar_expanded_width", p.sidebarExpandedWidth, 48, 260);
        headerHeight = range(l, "header_height", p.headerHeight, 0, 96);
        lineWidth = range(l, "line_width", p.lineWidth, 1, 8);
        nodeBorder = range(l, "node_border", p.nodeBorder, 1, 6);
        tabHeight = range(l, "tab_height", p.tabHeight, 16, 48);
        nodeIconScale = l.has("node_icon_scale")
                ? Math.clamp(l.get("node_icon_scale").getAsFloat(), .25f, .95f)
                : p.nodeIconScale;
        sidebarPosition = anchor(l, "sidebar", p.sidebarPosition, Anchor.LEFT, Anchor.RIGHT, Anchor.HIDDEN);
        titlePosition = anchor(
                l,
                "title",
                p.titlePosition,
                Anchor.TOP_LEFT,
                Anchor.TOP_CENTER,
                Anchor.TOP_RIGHT,
                Anchor.BOTTOM_LEFT,
                Anchor.BOTTOM_CENTER,
                Anchor.BOTTOM_RIGHT,
                Anchor.HIDDEN);
        controlsPosition = anchor(
                l,
                "controls",
                p.controlsPosition,
                Anchor.TOP_LEFT,
                Anchor.TOP_RIGHT,
                Anchor.BOTTOM_LEFT,
                Anchor.BOTTOM_RIGHT,
                Anchor.HIDDEN);
        actionsPosition = anchor(
                l,
                "actions",
                p.actionsPosition,
                Anchor.TOP_LEFT,
                Anchor.TOP_RIGHT,
                Anchor.BOTTOM_LEFT,
                Anchor.BOTTOM_RIGHT);
        blur = bool(o, "blur", p.blur);
        textShadow = bool(o, "text_shadow", p.textShadow);
        nodeProgressBars = bool(o, "node_progress_bars", p.nodeProgressBars);
        canvasGrid = bool(o, "canvas_grid", p.canvasGrid);
        directConnections = bool(o, "direct_connections", p.directConnections);
        flatButtons = bool(o, "flat_buttons", p.flatButtons);
        flatNodes = bool(o, "flat_nodes", p.flatNodes);
        sidebarProgress = bool(o, "sidebar_progress", p.sidebarProgress);
        nodeTitles = bool(o, "node_titles", p.nodeTitles);
        animations = bool(o, "animations", p.animations);
        arrowheads = bool(o, "arrowheads", p.arrowheads);
        chapterTitle = bool(o, "chapter_title", p.chapterTitle);
        lineOutlines = bool(o, "line_outlines", p.lineOutlines);
        lockedAlpha = o.has("locked_alpha") ? Math.clamp(o.get("locked_alpha").getAsFloat(), 0f, 1f) : p.lockedAlpha;
    }

    /** Builds a theme from JSON, inheriting omitted values from {@code parent}. */
    public static QuestTheme parse(JsonObject json, QuestTheme parent) {
        return new QuestTheme(json, parent);
    }

    /** Built-in shapes, then theme shapes, then a namespaced ID as {@code <id>_complete}/{@code <id>_incomplete} sprites. */
    public Shape shape(String id) {
        Shape shape = shapes.get(id);
        if (shape != null) return shape;
        Identifier parsed = id.contains(":") ? Identifier.tryParse(id) : null;
        Shape task = shapes.get("task");
        if (parsed != null)
            return new Shape(parsed.withSuffix("_complete"), parsed.withSuffix("_incomplete"), task.geometry(), 1f);
        return task;
    }

    private static Identifier mc(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        String value = json.get(key).getAsString().replace("#", "");
        if (value.length() != 6 && value.length() != 8)
            throw new IllegalArgumentException("Colour " + key + " must be #RRGGBB or #AARRGGBB");
        long parsed = Long.parseLong(value, 16);
        return (int) (value.length() == 6 ? 0xff000000L | parsed : parsed);
    }

    private static @Nullable Identifier id(JsonObject json, String key, @Nullable Identifier fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        if (value.isJsonNull()) return null;
        Identifier parsed = Identifier.tryParse(value.getAsString());
        if (parsed == null) throw new IllegalArgumentException("Invalid ID for " + key + ": " + value.getAsString());
        return parsed;
    }

    private static int range(JsonObject json, String key, int fallback, int min, int max) {
        return json.has(key) ? Math.clamp(json.get(key).getAsInt(), min, max) : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
    /** Reads {@code <name>_position}; only the listed anchors are accepted. */
    private static Anchor anchor(JsonObject json, String name, Anchor fallback, Anchor... allowed) {
        String key = name + "_position";
        if (!json.has(key)) return fallback;
        Anchor parsed = Anchor.parse(key, json.get(key).getAsString(), fallback);
        if (!Arrays.asList(allowed).contains(parsed))
            throw new IllegalArgumentException(
                    key + " cannot be " + parsed.name().toLowerCase(Locale.ROOT));
        return parsed;
    }
}
