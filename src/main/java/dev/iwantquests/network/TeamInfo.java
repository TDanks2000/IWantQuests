package dev.iwantquests.network;

import dev.iwantquests.network.PlayerSnapshot.Member;
import java.util.*;

/**
 * Team management view for one player, sent as {@code SyncPayload("team", json)} in reply to the
 * {@code team_info} action and after any team change that affects the player.
 * Team actions use {@link ActionPayload} with {@code quest} as the argument:
 * {@code team_info}, {@code team_create} (name), {@code team_invite} (player UUID), {@code team_cancel} (player UUID),
 * {@code team_accept}, {@code team_decline}, {@code team_leave}, {@code team_kick} (player UUID).
 *
 * @param members        team members, owner first
 * @param online         online member UUIDs
 * @param invitable      online players outside the team (owners only; empty otherwise)
 * @param invited        players with a pending invitation to this team
 * @param invite         pending invitation to another team for this player, or null
 */
public record TeamInfo(
        boolean solo,
        String name,
        UUID owner,
        boolean isOwner,
        List<Member> members,
        List<UUID> online,
        List<Member> invitable,
        List<UUID> invited,
        Invite invite) {
    /** @param expiresInMs milliseconds until the invitation expires */
    public record Invite(String teamName, String from, long expiresInMs) {}
}
