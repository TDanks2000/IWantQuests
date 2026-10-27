package dev.iwantquests.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.iwantquests.engine.ServerQuests;
import dev.iwantquests.network.PlayerSnapshot.Member;
import dev.iwantquests.network.SyncPayload;
import dev.iwantquests.network.TeamInfo;
import java.util.stream.Collectors;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuestCommands {
    @FunctionalInterface
    private interface Action {
        void run(ServerPlayer player, ServerQuests quests) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("iwantquests")
                .executes(c -> run(c, (p, s) -> {
                    s.initialSync(p);
                    PacketDistributor.sendToPlayer(p, new SyncPayload("open", ""));
                }))
                .then(Commands.literal("check")
                        .executes(c -> run(c, (p, s) -> {
                            s.checkPossession(p);
                            s.checkEnvironment(p);
                        })))
                .then(Commands.literal("team")
                        .executes(c -> run(c, (p, s) -> status(p, s)))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> run(
                                                c,
                                                (p, s) -> message(
                                                        p, s.teamCreate(p, StringArgumentType.getString(c, "name")))))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> run(
                                                c,
                                                (p, s) -> message(
                                                        p, s.teamInvite(p, EntityArgument.getPlayer(c, "player")))))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> run(
                                                c,
                                                (p, s) -> message(
                                                        p,
                                                        s.teamCancel(
                                                                p,
                                                                EntityArgument.getPlayer(c, "player")
                                                                        .getUUID()))))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> run(
                                                c,
                                                (p, s) -> message(
                                                        p,
                                                        s.teamKick(
                                                                p,
                                                                EntityArgument.getPlayer(c, "player")
                                                                        .getUUID()))))))
                        .then(Commands.literal("accept").executes(c -> run(c, (p, s) -> message(p, s.teamAccept(p)))))
                        .then(Commands.literal("decline").executes(c -> run(c, (p, s) -> message(p, s.teamDecline(p)))))
                        .then(Commands.literal("leave").executes(c -> run(c, (p, s) -> message(p, s.teamLeave(p)))))));
    }

    private static void status(ServerPlayer player, ServerQuests quests) {
        TeamInfo info = quests.teamInfo(player);
        String owner = info.members().stream()
                .filter(m -> m.id().equals(info.owner()))
                .map(Member::name)
                .findFirst()
                .orElse("Unknown");
        String names = info.members().stream().map(Member::name).collect(Collectors.joining(", "));
        message(
                player,
                "IWantQuests team: " + info.name() + " (" + info.members().size() + " members). Owner: " + owner
                        + ". Members: " + names + ".");
        TeamInfo.Invite invite = info.invite();
        if (invite != null)
            message(
                    player,
                    "Pending invitation to " + invite.teamName() + " from " + invite.from() + ", expires in "
                            + invite.expiresInMs() / 1000 + " seconds. Use /iwantquests team accept or decline.");
        message(player, "Commands: /iwantquests team create|invite|cancel|kick|accept|decline|leave");
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    private static int run(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            action.run(player, ServerQuests.get(context.getSource().getServer()));
            return 1;
        } catch (IllegalArgumentException error) {
            context.getSource().sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private QuestCommands() {}
}
