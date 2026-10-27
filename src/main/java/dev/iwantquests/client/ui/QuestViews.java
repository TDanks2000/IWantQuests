package dev.iwantquests.client.ui;

import dev.iwantquests.client.theme.QuestTheme;
import dev.iwantquests.network.PlayerSnapshot.QuestView;
import dev.iwantquests.quest.QuestPack.Objective;
import dev.iwantquests.quest.QuestPack.Quest;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** Turns a quest and the player's snapshot into status text, colours and progress. Null-safe for older states. */
public final class QuestViews {
    private static final QuestView EMPTY = new QuestView(Map.of(), false, false, false, Map.of());

    private QuestViews() {}

    /** Returns the view for a quest with every map guaranteed non-null. */
    public static QuestView view(@Nullable QuestView raw) {
        if (raw == null) return EMPTY;
        if (raw.objectives() != null && raw.contributions() != null) return raw;
        return new QuestView(
                map(raw.objectives()), raw.completed(), raw.claimed(), raw.available(), map(raw.contributions()));
    }

    public static boolean locked(QuestView view) {
        return !view.available() && !view.completed();
    }

    public static String status(QuestView view) {
        if (view.completed()) return QuestText.t(view.claimed() ? "status.complete" : "status.ready");
        return QuestText.t(view.available() ? "status.in_progress" : "status.locked");
    }

    public static int statusColor(QuestTheme theme, QuestView view) {
        if (view.completed()) return view.claimed() ? theme.complete : theme.ready;
        return view.available() ? theme.inProgress : theme.locked;
    }

    public static int done(QuestView view, Objective objective) {
        return Math.min(Math.max(1, objective.count()), view.objectives().getOrDefault(objective.id(), 0));
    }

    public static boolean actionable(Objective objective) {
        return "submit".equals(objective.type()) || "manual".equals(objective.type());
    }

    /** True when the objective target names a concrete item (not a #tag). */
    public static boolean itemTarget(Objective objective) {
        return objective.target() != null
                && !objective.target().isBlank()
                && !objective.target().startsWith("#");
    }

    /** Overall progress over all objectives of a quest, from 0 to 1. */
    public static double progress(Quest quest, QuestView view) {
        List<Objective> objectives = list(quest.objectives());
        if (objectives.isEmpty()) return view.completed() ? 1 : 0;
        double done = 0, total = 0;
        for (Objective objective : objectives) {
            done += done(view, objective);
            total += Math.max(1, objective.count());
        }
        return done / total;
    }

    public static String objectiveLabel(Objective objective) {
        if (objective.description() == null || objective.description().isBlank()) {
            return objective.type() + " " + objective.target();
        }
        return objective.description();
    }

    public static String shapeLabel(String shape) {
        String base = shape.contains(":") ? shape.substring(shape.indexOf(':') + 1) : shape;
        String key = "shape." + base.toLowerCase(Locale.ROOT);
        if (net.minecraft.locale.Language.getInstance().has("iwantquests.ui." + key)) return QuestText.t(key);
        return base.isEmpty() ? QuestText.t("shape.task") : Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    public static <T> List<T> list(@Nullable List<T> values) {
        return values == null ? List.of() : values;
    }

    public static String text(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Integer> map(@Nullable Map<String, Integer> values) {
        return values == null ? Map.of() : values;
    }
}
