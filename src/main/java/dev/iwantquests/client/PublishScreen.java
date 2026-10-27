package dev.iwantquests.client;

import dev.iwantquests.client.ui.QuestText;
import dev.iwantquests.edit.QuestDraft.Review;
import java.util.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Reviews the complete private diff; final confirmation is the only action that changes the published book. */
public final class PublishScreen extends QuestListScreen {
    private int revision;

    public PublishScreen(QuestBookScreen parent) {
        super(parent, QuestText.t("draft.title"));
        revision = ClientQuestState.revision;
    }

    private Review review() {
        return ClientQuestState.draft == null ? null : ClientQuestState.draft.review();
    }

    @Override
    protected String modeLabel() {
        return QuestText.t("draft.publish");
    }

    @Override
    protected boolean modeEnabled() {
        Review r = review();
        return r != null && !r.conflict() && r.changedAnything() && ClientQuestState.editsInFlight == 0;
    }

    @Override
    protected String footer() {
        Review r = review();
        return r == null ? QuestText.t("loading") : QuestText.t(r.conflict() ? "draft.conflict" : "draft.private");
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        Review r = review();
        if (r != null) {
            for (String value : r.added()) result.add(info(QuestText.t("draft.added"), value));
            for (String value : r.changed()) result.add(info(QuestText.t("draft.changed"), value));
            for (String value : r.removed()) result.add(info(QuestText.t("draft.removed"), value));
            for (String value : r.chapters()) result.add(info(QuestText.t("draft.chapter"), value));
            for (String value : r.progressWarnings()) result.add(info(QuestText.t("draft.warning"), value));
        }
        result.add(new Entry(QuestText.t("draft.discard"), QuestText.t("draft.discard_detail"), ItemStack.EMPTY, () -> {
            minecraft.gui.setScreen(new ConfirmScreen(
                    yes -> {
                        if (yes) ClientQuestState.edit("discard", "{}");
                        minecraft.gui.setScreen(yes ? parent : this);
                    },
                    Component.literal(QuestText.t("draft.discard")),
                    Component.literal(QuestText.t("draft.discard_detail"))));
        }));
        String term = query.toLowerCase(Locale.ROOT);
        return result.stream()
                .filter(e ->
                        (e.title() + " " + e.detail()).toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }

    private Entry info(String title, String detail) {
        return new Entry(title, detail, ItemStack.EMPTY, () -> {});
    }

    @Override
    protected void nextMode() {
        Review r = review();
        if (r == null || r.conflict() || !r.changedAnything() || ClientQuestState.editsInFlight > 0) return;
        minecraft.gui.setScreen(new ConfirmScreen(
                yes -> {
                    if (yes) ClientQuestState.edit("publish", "{}");
                    minecraft.gui.setScreen(yes ? parent : this);
                },
                Component.literal(QuestText.t("draft.publish")),
                Component.literal(QuestText.t(
                        "draft.confirm",
                        r.added().size(),
                        r.changed().size(),
                        r.removed().size(),
                        r.progressWarnings().size()))));
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
}
