package dev.iwantquests.client.ui;

import dev.iwantquests.quest.QuestPack.Quest;
import java.util.List;

/** Finds a free snapped position for a pasted group without changing its internal arrangement. */
public final class QuestPlacement {
    public record Position(double x, double y) {}

    public static Position find(
            List<Quest> source, List<Quest> occupied, double anchorX, double anchorY, double nodeSize) {
        if (source.isEmpty()) throw new IllegalArgumentException("Select quests to paste first.");
        double minX = source.stream().mapToDouble(Quest::x).min().orElse(0),
                minY = source.stream().mapToDouble(Quest::y).min().orElse(0);
        double startX = Math.rint(anchorX / 8) * 8, startY = Math.rint(anchorY / 8) * 8;
        for (int attempt = 0; attempt < 128; attempt++) {
            double x = startX + attempt * 48, y = startY + attempt * 48;
            boolean collision = false;
            for (Quest q : source) {
                double px = x + q.x() - minX, py = y + q.y() - minY;
                for (Quest old : occupied) {
                    double clearance = nodeSize * (q.sizeOrDefault() + old.sizeOrDefault()) / 2 + 12;
                    if (Math.abs(old.x() - px) < clearance && Math.abs(old.y() - py) < clearance) {
                        collision = true;
                        break;
                    }
                }
                if (collision) break;
            }
            if (!collision) return new Position(x, y);
        }
        throw new IllegalArgumentException("No nearby space for this selection. Pan to an empty area and paste again.");
    }

    private QuestPlacement() {}
}
