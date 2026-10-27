package dev.iwantquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.iwantquests.IWantQuests;
import dev.iwantquests.client.theme.ThemeManager;
import dev.iwantquests.network.SyncPayload;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = IWantQuests.MOD_ID, dist = Dist.CLIENT)
public final class IWantQuestsClient {
    private KeyMapping open;

    public IWantQuestsClient(IEventBus bus, ModContainer container) {
        if (!net.neoforged.fml.loading.FMLEnvironment.isProduction() && Boolean.getBoolean("iwantquests.smokeTest"))
            dev.iwantquests.testing.ClientSmokeTest.register();
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener((RegisterKeyMappingsEvent e) -> {
            var category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("iwantquests", "quests"));
            e.registerCategory(category);
            open = new KeyMapping("key.iwantquests.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category);
            e.register(open);
        });
        bus.addListener((AddClientReloadListenersEvent e) ->
                e.addListener(Identifier.fromNamespaceAndPath("iwantquests", "themes"), new ThemeManager.Loader()));
        bus.addListener((RegisterClientPayloadHandlersEvent e) ->
                e.register(SyncPayload.TYPE, (payload, context) -> ClientQuestState.receive(payload)));
        bus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(
                Identifier.fromNamespaceAndPath("iwantquests", "tracker"),
                (graphics, delta) -> QuestTracker.draw(graphics)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (open == null) return;
            var minecraft = Minecraft.getInstance();
            while (open.consumeClick())
                if (minecraft.player != null && minecraft.gui.screen() == null) {
                    ClientQuestState.action("sync", "", "");
                    minecraft.gui.setScreen(new QuestBookScreen());
                }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientQuestState.reset());
    }
}
