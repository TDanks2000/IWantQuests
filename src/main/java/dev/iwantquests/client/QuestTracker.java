package dev.iwantquests.client;

import dev.iwantquests.client.theme.ThemeManager;
import dev.iwantquests.client.ui.*;
import dev.iwantquests.quest.QuestPack.Quest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact live HUD, using authoritative progress and only published, player-visible quests. */
public final class QuestTracker {
    public static void draw(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.gui.screen() != null
                || mc.gui.hud.isHidden()
                || ClientQuestState.pack == null
                || ClientQuestState.snapshot == null
                || !ClientConfig.tracker()) return;
        List<String> pins = QuestPins.ids();
        List<Quest> visible = ClientQuestState.pack.quests().stream()
                .filter(q -> pins.contains(q.id()))
                .filter(q -> !q.hidden()
                        || QuestViews.view(ClientQuestState.snapshot.quests().get(q.id()))
                                .available()
                        || QuestViews.view(ClientQuestState.snapshot.quests().get(q.id()))
                                .completed())
                .limit(3)
                .toList();
        if (visible.isEmpty()) return;
        var t = ThemeManager.get(null);
        var qt = new QuestText(() -> t);
        int w = Math.min(180, mc.getWindow().getGuiScaledWidth() / 2 - 12), x = 8, y = 54;
        for (Quest q : visible) {
            var view = QuestViews.view(ClientQuestState.snapshot.quests().get(q.id()));
            int lines = Math.min(2, q.objectives().size());
            int h = 27 + lines * 12;
            if (y + h > mc.getWindow().getGuiScaledHeight() - 42) break;
            QuestDraw.box(g, x, y, w, h, t.panel | 0xe0000000, t.border);
            qt.drawClipped(g, q.title(), x + 6, y + 5, w - 12, QuestViews.statusColor(t, view));
            int lineY = y + 18;
            for (var objective : q.objectives().stream().limit(2).toList()) {
                String text = QuestViews.done(view, objective) + "/" + objective.count() + " "
                        + QuestViews.objectiveLabel(objective);
                qt.drawClipped(g, text, x + 6, lineY, w - 12, t.text);
                lineY += 12;
            }
            g.fill(x + 6, y + h - 5, x + w - 6, y + h - 3, t.progressBack);
            g.fill(x + 6, y + h - 5, x + 6 + (int) ((w - 12) * QuestViews.progress(q, view)), y + h - 3, t.accent);
            y += h + 5;
        }
    }

    private QuestTracker() {}
}
