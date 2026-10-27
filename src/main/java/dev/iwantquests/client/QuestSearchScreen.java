package dev.iwantquests.client;

import dev.iwantquests.client.ui.*;
import java.util.*;

/** Searches the whole visible book; hidden locked quests never appear in player search. */
public final class QuestSearchScreen extends QuestListScreen {
    private int filter;
    private static final String[] FILTERS = {
        "search.all", "search.available", "search.completed", "search.unclaimed", "search.pinned"
    };

    public QuestSearchScreen(QuestBookScreen parent) {
        super(parent, QuestText.t("search.title"));
    }

    @Override
    protected String modeLabel() {
        return QuestText.t(FILTERS[filter]);
    }

    @Override
    protected void nextMode() {
        filter = (filter + 1) % FILTERS.length;
    }

    @Override
    protected List<Entry> entries() {
        String term = query.toLowerCase(Locale.ROOT).strip();
        Map<String, String> chapters = new HashMap<>();
        parent.chapters().forEach(c -> chapters.put(c.id(), c.title()));
        return parent.quests().stream()
                .filter(q -> parent.editing()
                        || !q.hidden()
                        || parent.view(q).available()
                        || parent.view(q).completed())
                .filter(q -> switch (filter) {
                    case 1 -> parent.view(q).available() && !parent.view(q).completed();
                    case 2 -> parent.view(q).completed();
                    case 3 -> parent.view(q).completed() && !parent.view(q).claimed();
                    case 4 -> QuestPins.pinned(q.id());
                    default -> true;
                })
                .filter(q -> (q.title() + " " + QuestViews.text(q.subtitle()) + " " + QuestViews.text(q.description())
                                + " " + q.id() + " "
                                + q.objectives().stream()
                                        .map(o -> QuestViews.text(o.target()))
                                        .toList() + " " + chapters.getOrDefault(q.chapter(), q.chapter()))
                        .toLowerCase(Locale.ROOT)
                        .contains(term))
                .map(q -> new Entry(
                        q.title(),
                        chapters.getOrDefault(q.chapter(), q.chapter()) + " · " + QuestViews.status(parent.view(q)),
                        parent.icon(q.icon()),
                        () -> {
                            onClose();
                            parent.jumpToQuest(q.id());
                        }))
                .toList();
    }
}
