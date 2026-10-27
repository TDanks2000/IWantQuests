package dev.iwantquests.team;

import dev.iwantquests.progress.ProgressState;
import dev.iwantquests.progress.ProgressState.*;
import java.util.*;

public final class TeamService {
    private static final long INVITATION_MS = 300_000;
    private final ProgressState state;

    public TeamService(ProgressState state) {
        this.state = state;
    }

    public Team team(UUID player) {
        UUID id = state.membership.get(player);
        if (id != null) return state.teams.get(id);
        UUID solo = state.soloTeams.computeIfAbsent(player, p -> UUID.randomUUID());
        Team result = state.teams.computeIfAbsent(solo, t -> new Team(t, player, "Solo", true));
        result.members.add(player);
        state.membership.put(player, solo);
        return result;
    }

    public Team create(UUID player, String name) {
        Team old = team(player);
        if (!old.solo) throw new IllegalArgumentException("Leave your current team first.");
        if (name.isBlank() || name.length() > 32)
            throw new IllegalArgumentException("Team name must be 1–32 characters.");
        old.members.remove(player);
        Team created = new Team(UUID.randomUUID(), player, name, false);
        // Forming a team preserves the founder's solo progression without merging other teams.
        old.quests.forEach((id, progress) -> created.quests.put(id, copy(progress)));
        state.teams.put(created.id, created);
        state.membership.put(player, created.id);
        return created;
    }

    private static QuestProgress copy(QuestProgress source) {
        QuestProgress result = new QuestProgress();
        result.completed = source.completed;
        source.objectives.forEach((id, c) -> {
            Counter copy = new Counter(c.signature, c.value);
            copy.possessions.putAll(c.possessions);
            ProgressState.contributions(copy).putAll(ProgressState.contributions(c));
            result.objectives.put(id, copy);
        });
        return result;
    }
    /** Re-inviting a guest replaces any pending or expired invitation, which refreshes its five-minute window. */
    public void invite(UUID owner, UUID guest, long now) {
        Team team = requireOwner(owner, "Only a team owner can invite players.");
        if (owner.equals(guest) || team.members.contains(guest))
            throw new IllegalArgumentException("Player is already in your team.");
        state.invitations.put(guest, new Invitation(team.id, now + INVITATION_MS, owner));
    }

    public void cancelInvite(UUID owner, UUID guest) {
        Team team = requireOwner(owner, "Only a team owner can cancel invitations.");
        Invitation invitation = state.invitations.get(guest);
        if (invitation == null || !invitation.team().equals(team.id))
            throw new IllegalArgumentException("No pending invitation for that player.");
        state.invitations.remove(guest);
    }
    /** Returns the removed invitation so callers can refresh the team it pointed to. */
    public Invitation decline(UUID player) {
        Invitation invitation = state.invitations.remove(player);
        if (invitation == null) throw new IllegalArgumentException("You have no pending invitation.");
        return invitation;
    }

    public void accept(UUID player, long now) {
        Invitation invitation = state.invitations.get(player);
        if (invitation == null || invitation.expiresAt() < now)
            throw new IllegalArgumentException("No valid invitation. Invitations last five minutes.");
        Team destination = state.teams.get(invitation.team());
        if (destination == null || destination.members.isEmpty())
            throw new IllegalArgumentException("That team no longer exists.");
        Team old = team(player);
        if (!old.solo) throw new IllegalArgumentException("Leave your current team first.");
        old.members.remove(player);
        destination.members.add(player);
        state.membership.put(player, destination.id);
        state.invitations.remove(player);
    }

    public void leave(UUID player) {
        Team old = team(player);
        if (old.solo) throw new IllegalArgumentException("You are already playing solo.");
        old.members.remove(player);
        if (old.owner.equals(player) && !old.members.isEmpty())
            old.owner = old.members.iterator().next();
        state.membership.remove(player);
        state.invitations.remove(player);
        team(player);
    }

    public void kick(UUID owner, UUID member) {
        Team team = requireOwner(owner, "Only a team owner can kick players.");
        if (owner.equals(member)) throw new IllegalArgumentException("You cannot kick yourself. Use leave instead.");
        if (!team.members.contains(member)) throw new IllegalArgumentException("That player is not in your team.");
        // Same as leave: the member's solo team is restored with its own progress intact.
        team.members.remove(member);
        state.membership.remove(member);
        state.invitations.remove(member);
        team(member);
    }
    /** Guests with a non-expired invitation to this team. */
    public List<UUID> invited(Team team, long now) {
        List<UUID> guests = new ArrayList<>();
        for (UUID guest : new ArrayList<>(state.invitations.keySet())) {
            Invitation invitation = invitation(guest, now);
            if (invitation != null && invitation.team().equals(team.id)) guests.add(guest);
        }
        return guests;
    }
    /** The player's non-expired invitation, or null. Expired invitations are removed. */
    public Invitation invitation(UUID player, long now) {
        Invitation invitation = state.invitations.get(player);
        if (invitation != null && invitation.expiresAt() < now) {
            state.invitations.remove(player);
            return null;
        }
        return invitation;
    }

    private Team requireOwner(UUID player, String message) {
        Team team = team(player);
        if (team.solo || !team.owner.equals(player)) throw new IllegalArgumentException(message);
        return team;
    }
}
