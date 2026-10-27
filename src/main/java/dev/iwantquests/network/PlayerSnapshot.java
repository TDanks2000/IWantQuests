package dev.iwantquests.network;

import java.util.*;

/**
 * {@code memberList} lists every team member with their last known name.
 * {@code contributions} in a view maps member UUID strings to the progress that member added to the quest.
 */
public record PlayerSnapshot(
        UUID team, String teamName, int members, Map<String, QuestView> quests, List<Member> memberList) {
    public record QuestView(
            Map<String, Integer> objectives,
            boolean completed,
            boolean claimed,
            boolean available,
            Map<String, Integer> contributions) {}

    public record Member(UUID id, String name) {}
}
