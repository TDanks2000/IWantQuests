package dev.iwantquests.engine;

import com.google.gson.JsonParseException;
import dev.iwantquests.IWantQuests;
import dev.iwantquests.QuestConfig;
import dev.iwantquests.edit.QuestDraft;
import dev.iwantquests.edit.QuestEditor;
import dev.iwantquests.edit.QuestTemplates;
import dev.iwantquests.network.*;
import dev.iwantquests.network.PlayerSnapshot.Member;
import dev.iwantquests.network.PlayerSnapshot.QuestView;
import dev.iwantquests.objective.ObjectiveRegistry.Contribution;
import dev.iwantquests.progress.*;
import dev.iwantquests.progress.ProgressState.Invitation;
import dev.iwantquests.progress.ProgressState.Team;
import dev.iwantquests.quest.*;
import dev.iwantquests.quest.QuestPack.*;
import dev.iwantquests.reward.InventoryRewards;
import dev.iwantquests.reward.RewardResolver;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ServerQuests {
    private static final Map<MinecraftServer, ServerQuests> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final QuestSavedData saved;
    private QuestCatalog catalog;
    private final QuestEngine engine;
    private final Map<UUID, Long> lastAction = new HashMap<>();
    private final Map<UUID, Boolean> editorAccess = new HashMap<>();
    private final Map<UUID, QuestDraft> drafts = new HashMap<>();

    private QuestDraft draft(ServerPlayer player) {
        return drafts.computeIfAbsent(player.getUUID(), id -> {
            var config = FMLPaths.CONFIGDIR.get().resolve("iwantquests");
            return new QuestDraft(
                    config.resolve("quests"),
                    config.resolve("drafts").resolve(id.toString()),
                    config.resolve("backups"),
                    pack -> authored(id, pack),
                    QuestConfig.EDITOR_NAMESPACE::get);
        });
    }
    /** Validates an author's draft. Command rewards run with full permission, so only owners may add or change them. */
    public QuestCatalog authored(UUID author, QuestPack pack) {
        QuestCatalog built = QuestLoader.build(pack);
        ServerPlayer online = server.getPlayerList().getPlayer(author);
        if (online == null || !online.permissions().hasPermission(Permissions.COMMANDS_OWNER)) {
            Set<String> published = commands(catalog.pack());
            for (String command : commands(pack))
                if (!published.contains(command))
                    throw new IllegalArgumentException(
                            "Only server owners can add or change command rewards in the editor. Edit the quest files directly instead.");
        }
        return built;
    }

    private static Set<String> commands(QuestPack pack) {
        Set<String> result = new HashSet<>();
        for (Quest q : pack.quests()) if (q.rewards() != null) collectCommands(q.rewards(), result);
        return result;
    }

    private static void collectCommands(List<Reward> rewards, Set<String> into) {
        for (Reward reward : rewards) {
            if (reward == null) continue;
            if ("command".equals(reward.type()) && reward.target() != null) into.add(reward.target());
            collectCommands(reward.optionsOrEmpty(), into);
        }
    }

    private QuestTemplates templates() {
        return new QuestTemplates(FMLPaths.CONFIGDIR.get().resolve("iwantquests/templates"));
    }

    private ServerQuests(MinecraftServer server) {
        this.server = server;
        saved = server.getDataStorage().computeIfAbsent(QuestSavedData.TYPE);
        catalog = loaded(server);
        engine = new QuestEngine(saved.state(), catalog);
        saved.setDirty();
        validateTags();
    }

    private static QuestCatalog loaded(MinecraftServer server) {
        return server.getServerResources()
                .managers()
                .getListener(QuestLoader.KEY)
                .catalog();
    }

    public static ServerQuests get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ServerQuests::new);
    }

    public static void stop(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public QuestEngine engine() {
        return engine;
    }

    public void dirty() {
        saved.setDirty();
    }

    public void logout(UUID player) {
        lastAction.remove(player);
        editorAccess.remove(player);
        drafts.remove(player);
    }

    private void validateTags() {
        for (Quest q : catalog.quests())
            for (Objective o : q.objectives()) {
                if (o.target() != null && o.target().startsWith("#")) {
                    var tag = BuiltInRegistries.ITEM.get(TagKey.create(
                            Registries.ITEM, Identifier.parse(o.target().substring(1))));
                    if (tag.isEmpty() || tag.get().size() == 0)
                        IWantQuests.LOGGER.error(
                                "Quest {} objective {} references an empty or missing tag {}",
                                q.id(),
                                o.id(),
                                o.target());
                }
            }
    }

    public void refresh() {
        QuestCatalog current = loaded(server);
        if (current != catalog) {
            catalog = current;
            engine.reload(catalog);
            dirty();
            validateTags();
        }
    }

    public static Set<String> targets(ItemStack stack) {
        Set<String> targets = new HashSet<>();
        targets.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        stack.typeHolder().tags().forEach(tag -> targets.add("#" + tag.location()));
        return targets;
    }

    public static boolean matches(ItemStack stack, String target) {
        if (target.startsWith("#"))
            return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(target.substring(1))));
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(target);
    }

    public void contribute(ServerPlayer player, Contribution event) {
        Set<String> changed = engine.contribute(player.getUUID(), event);
        if (!changed.isEmpty()) {
            dirty();
            syncTeam(engine.teams().team(player.getUUID()), changed);
        }
    }

    public void item(ServerPlayer player, String type, ItemStack stack, int count) {
        if (!stack.isEmpty() && count > 0) contribute(player, Contribution.item(type, targets(stack), count));
    }

    public void checkEnvironment(ServerPlayer player) {
        String dimension = player.level().dimension().identifier().toString();
        contribute(
                player,
                new Contribution(
                        "dimension", Set.of(dimension), 1, dimension, player.getX(), player.getY(), player.getZ()));
        if (!catalog.matching("region").isEmpty())
            contribute(
                    player,
                    new Contribution("region", Set.of(), 1, dimension, player.getX(), player.getY(), player.getZ()));
    }

    public void checkPossession(ServerPlayer player) {
        // Explicit checks and inventory-related events only; no per-tick inventory scanning.
        Set<String> selectors = new HashSet<>();
        for (Quest q : catalog.matching("possess"))
            for (Objective o : q.objectives()) if (o.type().equals("possess")) selectors.add(o.target());
        for (String selector : selectors) {
            int count = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && matches(stack, selector)) count += stack.getCount();
            }
            contribute(player, Contribution.item("possess", Set.of(selector), count));
        }
    }

    public PlayerSnapshot snapshot(ServerPlayer player, Set<String> ids) {
        Team team = engine.teams().team(player.getUUID());
        Map<String, QuestView> views = new LinkedHashMap<>();
        for (Quest q : catalog.quests())
            if (ids == null || ids.contains(q.id())) {
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (Objective o : q.objectives()) counts.put(o.id(), engine.count(team, q, o));
                Map<String, Integer> contributions = new LinkedHashMap<>();
                engine.contributions(team, q).forEach((id, amount) -> contributions.put(id.toString(), amount));
                views.put(
                        q.id(),
                        new QuestView(
                                counts,
                                engine.completed(team, q.id()),
                                engine.claimed(player.getUUID(), q.id()),
                                engine.available(team, q),
                                contributions));
            }
        List<Member> members = new ArrayList<>();
        for (UUID id : team.members) members.add(new Member(id, memberName(id)));
        return new PlayerSnapshot(team.id, team.name, team.members.size(), views, members);
    }

    private String memberName(UUID id) {
        Map<UUID, String> names = saved.state().names;
        String name = names == null ? null : names.get(id);
        if (name == null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) name = online.getGameProfile().name();
        }
        return name != null ? name : id.toString().substring(0, 8);
    }

    public void initialSync(ServerPlayer player) {
        refresh();
        engine.teams().team(player.getUUID());
        if (saved.state().names == null) saved.state().names = new HashMap<>();
        saved.state().names.put(player.getUUID(), player.getGameProfile().name());
        dirty();
        send(
                player,
                new SyncPayload(
                        "definitions", QuestLoader.JSON.toJson(catalog.pack().withoutCommands())));
        syncPlayer(player, null);
        syncEditor(player);
    }

    public void syncPlayer(ServerPlayer player, Set<String> changed) {
        send(
                player,
                new SyncPayload(
                        changed == null ? "state" : "delta", QuestLoader.JSON.toJson(snapshot(player, changed))));
    }

    public void syncTeam(Team team, Set<String> changed) {
        // Include descendants for availability changes, but definitions never accompany progress deltas.
        Set<String> ids = changed == null ? null : new HashSet<>(changed);
        if (ids != null)
            for (Quest q : catalog.quests())
                if (q.prerequisites().stream().anyMatch(changed::contains)) ids.add(q.id());
        for (UUID member : team.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) syncPlayer(player, ids);
        }
    }
    /** Call after any membership change; {@code old} is the team the player belonged to before the change. */
    public void membershipChanged(Team old, UUID player) {
        dirty();
        engine.restartRepeatables(old);
        syncTeam(old, null);
        Team current = engine.teams().team(player);
        syncTeam(current, null);
        refreshTeamInfo(old);
        refreshTeamInfo(current);
        refreshTeamInfo(player);
    }

    public TeamInfo teamInfo(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Team team = engine.teams().team(player.getUUID());
        List<Member> members = new ArrayList<>();
        if (team.members.contains(team.owner)) members.add(new Member(team.owner, memberName(team.owner)));
        for (UUID id : team.members) if (!id.equals(team.owner)) members.add(new Member(id, memberName(id)));
        List<UUID> online = new ArrayList<>();
        for (Member member : members)
            if (server.getPlayerList().getPlayer(member.id()) != null) online.add(member.id());
        List<Member> invitable = new ArrayList<>();
        if (!team.solo && team.owner.equals(player.getUUID())) {
            for (ServerPlayer other : server.getPlayerList().getPlayers())
                if (!team.members.contains(other.getUUID()))
                    invitable.add(
                            new Member(other.getUUID(), other.getGameProfile().name()));
            invitable.sort(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER));
        }
        List<UUID> invited = engine.teams().invited(team, now);
        TeamInfo.Invite invite = null;
        Invitation pending = engine.teams().invitation(player.getUUID(), now);
        if (pending != null && !pending.team().equals(team.id)) {
            Team destination = saved.state().teams.get(pending.team());
            if (destination != null)
                invite = new TeamInfo.Invite(
                        destination.name,
                        pending.from() == null ? "Someone" : memberName(pending.from()),
                        Math.max(0, pending.expiresAt() - now));
        }
        return new TeamInfo(
                team.solo,
                team.name,
                team.owner,
                team.owner.equals(player.getUUID()),
                members,
                online,
                invitable,
                invited,
                invite);
    }

    public void syncTeamInfo(ServerPlayer player) {
        send(player, new SyncPayload("team", QuestLoader.JSON.toJson(teamInfo(player))));
    }

    private void refreshTeamInfo(Team team) {
        for (UUID id : team.members) refreshTeamInfo(id);
    }

    private void refreshTeamInfo(UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) syncTeamInfo(online);
    }
    /** Sends the invitation message with clickable accept and decline buttons. Used by the command and the GUI action. */
    public void notifyInvite(ServerPlayer from, ServerPlayer guest) {
        String team = engine.teams().team(from.getUUID()).name;
        guest.sendSystemMessage(Component.literal(from.getName().getString() + " invited you to join " + team + ". ")
                .append(inviteButton("[Accept]", "/iwantquests team accept", ChatFormatting.GREEN, "Join " + team))
                .append(" ")
                .append(inviteButton(
                        "[Decline]", "/iwantquests team decline", ChatFormatting.RED, "Decline this invitation"))
                .append(" Expires in 5 minutes."));
    }

    private static MutableComponent inviteButton(String label, String command, ChatFormatting color, String hover) {
        return Component.literal(label)
                .withStyle(style -> style.withColor(color)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
    // Team operations shared by /iwantquests team commands and GUI actions. Each returns the player-facing result text.
    public String teamCreate(ServerPlayer player, String name) {
        Team old = engine.teams().team(player.getUUID());
        engine.teams().create(player.getUUID(), name);
        membershipChanged(old, player.getUUID());
        return "Team created.";
    }

    public String teamInvite(ServerPlayer owner, ServerPlayer guest) {
        engine.teams().invite(owner.getUUID(), guest.getUUID(), System.currentTimeMillis());
        dirty();
        notifyInvite(owner, guest);
        refreshTeamInfo(engine.teams().team(owner.getUUID()));
        syncTeamInfo(guest);
        return "Invitation sent.";
    }

    public String teamCancel(ServerPlayer owner, UUID guest) {
        engine.teams().cancelInvite(owner.getUUID(), guest);
        dirty();
        refreshTeamInfo(engine.teams().team(owner.getUUID()));
        refreshTeamInfo(guest);
        return "Invitation cancelled.";
    }

    public String teamKick(ServerPlayer owner, UUID member) {
        Team old = engine.teams().team(owner.getUUID());
        String team = old.name;
        engine.teams().kick(owner.getUUID(), member);
        membershipChanged(old, member);
        ServerPlayer kicked = server.getPlayerList().getPlayer(member);
        if (kicked != null)
            kicked.sendSystemMessage(
                    Component.literal("You were removed from " + team + ". Your solo progression was restored."));
        return "Removed " + memberName(member) + " from the team.";
    }

    public String teamAccept(ServerPlayer player) {
        Team old = engine.teams().team(player.getUUID());
        engine.teams().accept(player.getUUID(), System.currentTimeMillis());
        membershipChanged(old, player.getUUID());
        return "Joined team. Your personal reward claims remain unchanged.";
    }

    public String teamDecline(ServerPlayer player) {
        Invitation declined = engine.teams().decline(player.getUUID());
        dirty();
        Team team = saved.state().teams.get(declined.team());
        if (team != null) refreshTeamInfo(team);
        syncTeamInfo(player);
        return "Invitation declined.";
    }

    public String teamLeave(ServerPlayer player) {
        Team old = engine.teams().team(player.getUUID());
        engine.teams().leave(player.getUUID());
        membershipChanged(old, player.getUUID());
        return "Left team. Restored your solo progression.";
    }
    // ---------------------------------------------------------------- quest editor

    /** Authoring is opt-in on the server; creative mode and team ownership grant no access. */
    public static boolean canEdit(ServerPlayer player) {
        return QuestConfig.EDITOR_ENABLED.get()
                && (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                        || QuestConfig.EDITOR_PLAYERS.get().stream()
                                .anyMatch(id ->
                                        id.equalsIgnoreCase(player.getUUID().toString())));
    }

    public EditorInfo editorInfo(ServerPlayer player) {
        if (!canEdit(player)) return EditorInfo.NONE;
        QuestDraft draft = draft(player);
        draft.pack(catalog.pack());
        QuestEditor.Editable editable = draft.editor().editable();
        return new EditorInfo(
                true,
                List.copyOf(editable.quests()),
                List.copyOf(editable.chapters()),
                draft.editor().canUndo(),
                draft.editor().canRedo());
    }

    public void syncEditor(ServerPlayer player) {
        EditorInfo info = editorInfo(player);
        editorAccess.put(player.getUUID(), info.enabled());
        send(player, new SyncPayload("editor", QuestLoader.JSON.toJson(info)));
        if (info.enabled()) {
            QuestDraft draft = draft(player);
            send(player, new SyncPayload("draft_definitions", QuestLoader.JSON.toJson(draft.pack(catalog.pack()))));
            send(
                    player,
                    new SyncPayload(
                            "draft_info", QuestLoader.JSON.toJson(new DraftInfo(draft.review(catalog.pack())))));
            send(
                    player,
                    new SyncPayload(
                            "templates", QuestLoader.JSON.toJson(templates().list())));
        }
    }

    /** Reflect operator/config changes in an open book without repeatedly scanning quest files. */
    public void refreshEditorAccess(ServerPlayer player) {
        if (!Objects.equals(editorAccess.get(player.getUUID()), canEdit(player))) syncEditor(player);
    }

    public void edit(ServerPlayer player, EditPayload request) {
        if (!canEdit(player)) {
            syncEditor(player);
            editResult(
                    player,
                    false,
                    "Quest editing requires editorEnabled and operator permission or a trusted author UUID in the server config.",
                    null);
            return;
        }
        try {
            refresh();
            QuestDraft draft = draft(player);
            if (request.op().equals("discard")) {
                draft.discard();
                syncEditor(player);
                editResult(player, true, "Draft discarded. Published quests are unchanged.", null);
                return;
            }
            QuestPack live = draft.pack(catalog.pack());
            QuestEditor author = draft.editor();
            if (request.op().equals("save_template")) {
                templates().save(body(request, QuestTemplates.Request.class), live.quests());
                syncEditor(player);
                editResult(player, true, "Saved reusable quest template.", null);
                return;
            }
            if (request.op().equals("delete_template")) {
                templates().delete(body(request, QuestTemplates.Delete.class).id());
                syncEditor(player);
                editResult(player, true, "Deleted quest template.", null);
                return;
            }
            if (request.op().equals("draft_open")) {
                syncEditor(player);
                editResult(player, true, "Private draft ready. Changes stay private until published.", null);
                return;
            }
            if (request.op().equals("publish")) {
                QuestEditor.Outcome published = draft.publish(catalog.pack());
                server.getServerResources()
                        .managers()
                        .getListener(QuestLoader.KEY)
                        .replace(published.catalog());
                catalog = published.catalog();
                engine.reload(catalog);
                dirty();
                validateTags();
                IWantQuests.LOGGER.info(
                        "{} published a quest draft", player.getGameProfile().name());
                for (ServerPlayer online : server.getPlayerList().getPlayers()) initialSync(online);
                editResult(player, true, published.message(), null);
                return;
            }
            QuestEditor.Outcome outcome = switch (request.op()) {
                case "patch" -> author.patch(live, body(request, QuestEditor.PatchRequest.class));
                case "create_quest" -> author.createQuest(live, body(request, QuestEditor.NewQuest.class));
                case "create_chapter" -> author.createChapter(live, body(request, QuestEditor.NewChapter.class));
                case "paste" -> author.paste(live, body(request, QuestEditor.PasteRequest.class));
                case "delete" -> author.delete(live, body(request, QuestEditor.DeleteRequest.class));
                case "undo" -> author.undo(live);
                case "redo" -> author.redo(live);
                default -> throw new IllegalArgumentException("Unknown editor operation.");
            };
            IWantQuests.LOGGER.info(
                    "{} edited private quest draft: {}", player.getGameProfile().name(), outcome.message());
            syncEditor(player);
            editResult(player, true, outcome.message(), outcome.select());
        } catch (IllegalArgumentException error) {
            editResult(
                    player, false, error.getMessage() == null ? "The change was rejected." : error.getMessage(), null);
        } catch (RuntimeException error) {
            IWantQuests.LOGGER.error(
                    "Quest editor request '{}' from {} failed",
                    request.op(),
                    player.getGameProfile().name(),
                    error);
            editResult(player, false, "The change could not be applied. See the server log for details.", null);
        }
    }

    private static <T> T body(EditPayload request, Class<T> type) {
        try {
            T value = QuestLoader.JSON.fromJson(request.json(), type);
            if (value == null) throw new IllegalArgumentException("Missing request body.");
            return value;
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("Malformed editor request.", error);
        }
    }

    private void editResult(ServerPlayer player, boolean ok, String message, String select) {
        send(player, new SyncPayload("edit_result", QuestLoader.JSON.toJson(new EditResult(ok, message, select))));
    }

    public void handle(ServerPlayer player, ActionPayload request) {
        long now = System.currentTimeMillis();
        if (now - lastAction.getOrDefault(player.getUUID(), 0L) < 150) return;
        lastAction.put(player.getUUID(), now);
        try {
            switch (request.action()) {
                case "sync" -> initialSync(player);
                case "claim" -> {
                    boolean restarted = engine.claim(
                            player.getUUID(),
                            request.quest(),
                            new InventoryRewards(player, RewardResolver.parseChoices(request.objective())));
                    dirty();
                    // Claim requests are rare: persist the player and claim ledger together through a server save.
                    server.saveEverything(true, false, true);
                    if (restarted) syncTeam(engine.teams().team(player.getUUID()), Set.of(request.quest()));
                    else syncPlayer(player, Set.of(request.quest()));
                    result(
                            player,
                            catalog.quest(request.quest()).repeatable() && !restarted
                                    ? "Rewards claimed. This quest starts over once every teammate has claimed."
                                    : "Rewards claimed.");
                }
                case "check" -> {
                    checkPossession(player);
                    checkEnvironment(player);
                    result(player, "Inventory and location checked.");
                }
                case "submit", "manual" -> objectiveAction(player, request);
                case "team_info" -> syncTeamInfo(player);
                case "team_create" -> result(player, teamCreate(player, request.quest()));
                case "team_invite" -> result(player, teamInvite(player, onlinePlayer(request.quest())));
                case "team_cancel" -> result(player, teamCancel(player, uuid(request.quest())));
                case "team_kick" -> result(player, teamKick(player, uuid(request.quest())));
                case "team_accept" -> result(player, teamAccept(player));
                case "team_decline" -> result(player, teamDecline(player));
                case "team_leave" -> result(player, teamLeave(player));
                default -> throw new IllegalArgumentException("Unknown quest action.");
            }
        } catch (IllegalArgumentException error) {
            result(player, error.getMessage());
        } catch (RuntimeException error) {
            IWantQuests.LOGGER.error(
                    "Quest action '{}' from {} failed",
                    request.action(),
                    player.getGameProfile().name(),
                    error);
            result(player, "That action could not be completed. See the server log for details.");
        }
    }

    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown player.");
        }
    }

    private ServerPlayer onlinePlayer(String text) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid(text));
        if (online == null) throw new IllegalArgumentException("Unknown player.");
        return online;
    }

    private void objectiveAction(ServerPlayer player, ActionPayload request) {
        Quest quest = catalog.quest(request.quest());
        Team team = engine.teams().team(player.getUUID());
        if (quest == null || !engine.available(team, quest) || engine.completed(team, quest.id()))
            throw new IllegalArgumentException("This quest is not available.");
        Objective objective = quest.objectives().stream()
                .filter(o -> o.id().equals(request.objective()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown objective."));
        if (!objective.type().equals(request.action()))
            throw new IllegalArgumentException("Objective action does not match.");
        int remaining = objective.count() - engine.count(team, quest, objective);
        if (remaining <= 0) throw new IllegalArgumentException("Objective is already complete.");
        if (request.action().equals("submit")) {
            int held = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && matches(stack, objective.target())) held += stack.getCount();
            }
            if (held < remaining)
                throw new IllegalArgumentException("You need " + remaining + " matching items to submit.");
            int needed = remaining;
            for (int i = 0; i < player.getInventory().getContainerSize() && needed > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && matches(stack, objective.target())) {
                    int take = Math.min(needed, stack.getCount());
                    stack.shrink(take);
                    needed -= take;
                }
            }
            player.inventoryMenu.broadcastChanges();
        }
        // Target one objective: submission must never advance other matching objectives for free.
        Set<String> changed = engine.completeAction(player.getUUID(), quest.id(), objective.id(), remaining);
        dirty();
        syncTeam(team, changed);
        result(player, "Objective completed.");
    }

    private static void send(ServerPlayer player, SyncPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(SyncPayload.TYPE)) return;
        for (SyncPayload part : SyncPayload.split(payload.kind(), payload.json()))
            PacketDistributor.sendToPlayer(player, part);
    }

    private void result(ServerPlayer player, String message) {
        send(player, new SyncPayload("result", QuestLoader.JSON.toJson(message)));
    }
}
