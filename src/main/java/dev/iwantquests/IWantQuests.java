package dev.iwantquests;

import com.mojang.logging.LogUtils;
import dev.iwantquests.commands.QuestCommands;
import dev.iwantquests.engine.ServerQuests;
import dev.iwantquests.network.*;
import dev.iwantquests.objective.ObjectiveRegistry.Contribution;
import dev.iwantquests.quest.QuestLoader;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(IWantQuests.MOD_ID)
public final class IWantQuests {
    public static final String MOD_ID = "iwantquests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IWantQuests(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, QuestConfig.SPEC);
        bus.addListener(this::payloads);
        if (!net.neoforged.fml.loading.FMLEnvironment.isProduction())
            dev.iwantquests.testing.QuestGameTests.register(bus);
        NeoForge.EVENT_BUS.addListener(
                (AddServerReloadListenersEvent e) -> e.addRetainedListener(QuestLoader.KEY, new QuestLoader()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(this::sync);
        NeoForge.EVENT_BUS.addListener(this::pickup);
        NeoForge.EVENT_BUS.addListener(this::craft);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::kill);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (e.getEntity() instanceof ServerPlayer p) quests(p).checkPossession(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                quests(p).checkEnvironment(p);
                quests(p).checkPossession(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) quests(p).logout(p.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) quests(p).checkEnvironment(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ServerQuests.stop(e.getServer()));
    }

    private static ServerQuests quests(ServerPlayer p) {
        return ServerQuests.get(p.level().getServer());
    }

    private void payloads(RegisterPayloadHandlersEvent e) {
        var registrar = e.registrar("1.0.0");
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.CODEC);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer p) quests(p).handle(p, payload);
        });
        registrar.playToServer(EditPayload.TYPE, EditPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer p) quests(p).edit(p, payload);
        });
    }

    private void sync(OnDatapackSyncEvent e) {
        e.getRelevantPlayers().forEach(p -> quests(p).initialSync(p));
    }

    private void pickup(ItemEntityPickupEvent.Post e) {
        if (e.getPlayer() instanceof ServerPlayer p) {
            var original = e.getOriginalStack();
            int count = original.getCount() - e.getCurrentStack().getCount();
            quests(p).item(p, "obtain", original, count);
            quests(p).checkPossession(p);
        }
    }

    private void craft(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            quests(p).item(p, "craft", e.getCrafting(), e.getCrafting().getCount());
            quests(p).item(p, "obtain", e.getCrafting(), e.getCrafting().getCount());
        }
    }

    private void kill(LivingDeathEvent e) {
        if (!e.isCanceled() && e.getSource().getEntity() instanceof ServerPlayer p)
            quests(p)
                    .contribute(
                            p,
                            Contribution.item(
                                    "kill",
                                    Set.of(BuiltInRegistries.ENTITY_TYPE
                                            .getKey(e.getEntity().getType())
                                            .toString()),
                                    1));
    }

    private void tick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) {
            var service = quests(p);
            service.refreshEditorAccess(p);
            if (!service.engine().catalog().matching("region").isEmpty()
                    || !service.engine().catalog().matching("dimension").isEmpty()) service.checkEnvironment(p);
        }
    }
}
