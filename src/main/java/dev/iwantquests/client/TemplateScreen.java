package dev.iwantquests.client;

import dev.iwantquests.client.ui.QuestText;
import java.util.*;

/** Select a reusable server template and place its group in the active chapter. */
public final class TemplateScreen extends QuestListScreen {
    private boolean delete;
    private int revision;

    public TemplateScreen(QuestBookScreen parent) {
        super(parent, QuestText.t("templates.title"));
        revision = ClientQuestState.revision;
    }

    @Override
    protected String modeLabel() {
        return QuestText.t(delete ? "templates.delete" : "templates.place");
    }

    @Override
    protected void nextMode() {
        delete = !delete;
    }

    @Override
    public void tick() {
        if (!ClientQuestState.editor.enabled()) {
            onClose();
            return;
        }
        if (revision != ClientQuestState.revision) {
            revision = ClientQuestState.revision;
            refresh();
        }
    }

    @Override
    protected List<Entry> entries() {
        return ClientQuestState.templates.stream()
                .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .map(t -> new Entry(
                        t.name(),
                        QuestText.t("templates.count", t.quests().size()),
                        parent.icon(t.quests().getFirst().icon()),
                        () -> {
                            if (delete)
                                minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                                        yes -> {
                                            if (yes)
                                                ClientQuestState.edit(
                                                        "delete_template",
                                                        dev.iwantquests.quest.QuestLoader.JSON.toJson(
                                                                new dev.iwantquests.edit.QuestTemplates.Delete(
                                                                        t.id())));
                                            minecraft.gui.setScreen(this);
                                        },
                                        net.minecraft.network.chat.Component.literal(QuestText.t("templates.delete")),
                                        net.minecraft.network.chat.Component.literal(t.name())));
                            else {
                                if (parent.editor() != null) parent.editor().paste(t.quests());
                                onClose();
                            }
                        }))
                .toList();
    }
}
