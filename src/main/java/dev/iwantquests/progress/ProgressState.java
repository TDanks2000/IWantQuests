package dev.iwantquests.progress;

import java.util.*;

/** Persisted domain state. Never store inventory or client-derived progress here. */
public final class ProgressState {
    public int version = 1;
    public Map<UUID, Team> teams = new LinkedHashMap<>();
    public Map<UUID, UUID> membership = new HashMap<>();
    public Map<UUID, UUID> soloTeams = new HashMap<>();
    // Player-wide claims deliberately survive team switches and quest definition changes.
    public Map<UUID, Set<String>> claims = new HashMap<>();
    public Map<UUID, Invitation> invitations = new HashMap<>();
    // Last known display names for team members, used by the quest book. May be null in old saves.
    public Map<UUID, String> names = new HashMap<>();
    /** Gson may leave optional maps null when loading older saves, so every access goes through here. */
    public static Map<UUID, Integer> contributions(Counter counter) {
        if (counter.contributions == null) counter.contributions = new HashMap<>();
        return counter.contributions;
    }

    public static final class Team {
        public UUID id;
        public UUID owner;
        public String name;
        public boolean solo;
        public Set<UUID> members = new LinkedHashSet<>();
        public Map<String, QuestProgress> quests = new HashMap<>();

        public Team(UUID id, UUID owner, String name, boolean solo) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.solo = solo;
            members.add(owner);
        }
    }

    public static final class QuestProgress {
        public Map<String, Counter> objectives = new HashMap<>();
        public boolean completed;
        /** Team members who claimed the current round of a repeatable quest. Null until the first claim, and in older saves. */
        public Set<UUID> claimedBy;
        /** How many times a repeatable quest has started over. */
        public int rounds;
    }

    public static final class Counter {
        public String signature;
        public int value;
        public Map<UUID, Integer> possessions = new HashMap<>();
        // Amount each player added to a non-possession objective; team members only are summed for display.
        public Map<UUID, Integer> contributions = new HashMap<>();

        public Counter(String signature, int value) {
            this.signature = signature;
            this.value = value;
        }
    }
    /** {@code from} is the inviting owner; null in saves written before invitations recorded it. */
    public record Invitation(UUID team, long expiresAt, UUID from) {}
}
