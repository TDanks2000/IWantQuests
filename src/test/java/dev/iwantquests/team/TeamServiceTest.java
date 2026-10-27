package dev.iwantquests.team;

import static org.junit.jupiter.api.Assertions.*;

import dev.iwantquests.progress.ProgressState;
import dev.iwantquests.progress.ProgressState.*;
import java.util.*;
import org.junit.jupiter.api.*;

class TeamServiceTest {
    private static final long T0 = 1_000;
    private static final long FIVE_MINUTES = 300_000;
    private ProgressState state;
    private TeamService teams;
    private UUID owner, guest, third;

    @BeforeEach
    void setup() {
        state = new ProgressState();
        teams = new TeamService(state);
        owner = UUID.randomUUID();
        guest = UUID.randomUUID();
        third = UUID.randomUUID();
    }

    private void team() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        teams.accept(guest, T0 + 1);
    }

    private static QuestProgress progress(boolean completed) {
        QuestProgress result = new QuestProgress();
        result.completed = completed;
        return result;
    }

    @Test
    void inviteStoresSender() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        Invitation invitation = teams.invitation(guest, T0);
        assertNotNull(invitation);
        assertEquals(owner, invitation.from());
        assertEquals(teams.team(owner).id, invitation.team());
        assertEquals(T0 + FIVE_MINUTES, invitation.expiresAt());
    }

    @Test
    void reinviteRefreshesExpiry() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        teams.invite(owner, guest, T0 + 200_000);
        Invitation invitation = teams.invitation(guest, T0 + 250_000);
        assertNotNull(invitation);
        assertEquals(T0 + 200_000 + FIVE_MINUTES, invitation.expiresAt());
    }

    @Test
    void reinviteAfterExpiryCreatesFreshInvitation() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        teams.invite(owner, guest, T0 + 400_000);
        assertNotNull(teams.invitation(guest, T0 + 400_001));
        teams.accept(guest, T0 + 400_001);
        assertEquals(teams.team(owner).id, teams.team(guest).id);
    }

    @Test
    void declineRemovesInvitation() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        teams.decline(guest);
        assertNull(teams.invitation(guest, T0));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> teams.decline(guest));
        assertEquals("You have no pending invitation.", error.getMessage());
    }

    @Test
    void cancelOnlyByOwnerForOwnTeam() {
        team();
        teams.invite(owner, third, T0);
        IllegalArgumentException notOwner =
                assertThrows(IllegalArgumentException.class, () -> teams.cancelInvite(guest, third));
        assertEquals("Only a team owner can cancel invitations.", notOwner.getMessage());
        UUID other = UUID.randomUUID();
        teams.create(other, "Other");
        IllegalArgumentException wrongTeam =
                assertThrows(IllegalArgumentException.class, () -> teams.cancelInvite(other, third));
        assertEquals("No pending invitation for that player.", wrongTeam.getMessage());
        teams.cancelInvite(owner, third);
        assertNull(teams.invitation(third, T0));
    }

    @Test
    void kickReturnsMemberToSoloWithProgress() {
        teams.team(guest).quests.put("iwantquests:first_steps", progress(true));
        team();
        teams.kick(owner, guest);
        Team solo = teams.team(guest);
        assertTrue(solo.solo);
        assertEquals(guest, solo.owner);
        assertTrue(solo.quests.get("iwantquests:first_steps").completed);
        assertFalse(teams.team(owner).members.contains(guest));
        assertNotEquals(teams.team(owner).id, teams.team(guest).id);
    }

    @Test
    void ownerCannotKickSelf() {
        team();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> teams.kick(owner, owner));
        assertEquals("You cannot kick yourself. Use leave instead.", error.getMessage());
    }

    @Test
    void nonOwnerCannotKick() {
        team();
        UUID stranger = UUID.randomUUID();
        IllegalArgumentException memberKick =
                assertThrows(IllegalArgumentException.class, () -> teams.kick(guest, owner));
        assertEquals("Only a team owner can kick players.", memberKick.getMessage());
        IllegalArgumentException notMember =
                assertThrows(IllegalArgumentException.class, () -> teams.kick(owner, stranger));
        assertEquals("That player is not in your team.", notMember.getMessage());
        assertTrue(teams.team(owner).members.contains(guest));
    }

    @Test
    void invitedExcludesExpired() {
        teams.create(owner, "Explorers");
        teams.invite(owner, guest, T0);
        teams.invite(owner, third, T0 + 200_000);
        Team team = teams.team(owner);
        List<UUID> invited = teams.invited(team, T0 + 400_000);
        assertEquals(List.of(third), invited);
        assertFalse(state.invitations.containsKey(guest));
    }

    @Test
    void oldSaveInvitationWithoutSenderStillWorks() {
        teams.create(owner, "Explorers");
        state.invitations.put(guest, new Invitation(teams.team(owner).id, T0 + FIVE_MINUTES, null));
        Invitation invitation = teams.invitation(guest, T0);
        assertNotNull(invitation);
        assertNull(invitation.from());
        assertEquals(List.of(guest), teams.invited(teams.team(owner), T0));
        teams.accept(guest, T0);
        assertEquals(teams.team(owner).id, teams.team(guest).id);
    }
}
