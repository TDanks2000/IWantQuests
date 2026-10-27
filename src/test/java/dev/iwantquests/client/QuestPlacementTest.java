package dev.iwantquests.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.iwantquests.client.ui.QuestPlacement;
import dev.iwantquests.quest.QuestPack.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuestPlacementTest {
    private static Quest q(double x, double y, double size) {
        return new Quest(
                "iwantquests:q",
                "iwantquests:c",
                "Quest",
                "",
                "minecraft:book",
                x,
                y,
                List.of(),
                List.of(Objective.simple("main", "manual", null, 1, "")),
                List.of(),
                false,
                false,
                false,
                false,
                null,
                null,
                size,
                null);
    }

    @Test
    void repeatedPastesFindDifferentFreePositions() {
        var original = q(0, 0, 1);
        List<Quest> occupied = new ArrayList<>(List.of(original));
        var first = QuestPlacement.find(List.of(original), occupied, 48, 48, 32);
        occupied.add(q(first.x(), first.y(), 1));
        var next = QuestPlacement.find(List.of(original), occupied, 48, 48, 32);
        assertNotEquals(first, next);
        assertTrue(Math.abs(first.x() - next.x()) >= 44);
    }

    @Test
    void largeNodesKeepEnoughClearance() {
        var large = q(0, 0, 3);
        var result = QuestPlacement.find(List.of(large), List.of(large), 0, 0, 32);
        assertTrue(result.x() >= 108 || result.y() >= 108);
    }

    @Test
    void aFullSearchAreaFailsRatherThanStackingQuests() {
        List<Quest> occupied = new ArrayList<>();
        for (int i = 0; i < 128; i++) occupied.add(q(i * 48, i * 48, 1));
        assertThrows(
                IllegalArgumentException.class, () -> QuestPlacement.find(List.of(q(0, 0, 1)), occupied, 0, 0, 32));
    }
}
