package dev.iwantquests.reward;

import dev.iwantquests.IWantQuests;
import dev.iwantquests.quest.QuestPack.Reward;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Providers must support deterministic preflight and must not execute arbitrary client data.
 * {@code random} and {@code choice} rewards are resolved into one of these before delivery; see {@link RewardResolver}.
 */
public final class RewardRegistry {
    public interface Provider {
        List<ItemStack> items(Reward reward);
        /** Items for one claim by {@code player}. Called once per claim; the result is what gets delivered. */
        default List<ItemStack> items(ServerPlayer player, Reward reward) {
            return items(reward);
        }

        void afterItems(ServerPlayer player, Reward reward);
    }

    private static final Map<String, Provider> TYPES = new HashMap<>();

    static {
        register("item", new Provider() {
            public List<ItemStack> items(Reward reward) {
                ItemStack prototype = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.target())));
                List<ItemStack> result = new ArrayList<>();
                int remaining = reward.count();
                while (remaining > 0) {
                    int size = Math.min(remaining, prototype.getMaxStackSize());
                    result.add(prototype.copyWithCount(size));
                    remaining -= size;
                }
                return result;
            }

            public void afterItems(ServerPlayer player, Reward reward) {}
        });
        register("xp", new Provider() {
            public List<ItemStack> items(Reward reward) {
                return List.of();
            }

            public void afterItems(ServerPlayer player, Reward reward) {
                player.giveExperiencePoints(reward.count());
            }
        });
        register("loot_table", new Provider() {
            public List<ItemStack> items(Reward reward) {
                return List.of();
            }

            @Override
            public List<ItemStack> items(ServerPlayer player, Reward reward) {
                var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(reward.target()));
                LootTable table =
                        player.level().getServer().reloadableRegistries().getLootTable(key);
                if (table == LootTable.EMPTY)
                    throw new IllegalArgumentException(
                            "The loot table for this reward does not exist: " + reward.target());
                LootParams params = new LootParams.Builder(player.level())
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                        .create(LootContextParamSets.CHEST);
                List<ItemStack> result = new ArrayList<>();
                for (int i = 0; i < reward.count(); i++) result.addAll(table.getRandomItems(params));
                return result;
            }

            public void afterItems(ServerPlayer player, Reward reward) {}
        });
        // Quest files are written by the server's administrators, so commands run with full permission as the player.
        // The in-game editor only lets owners add or change them; see ServerQuests.
        register("command", new Provider() {
            public List<ItemStack> items(Reward reward) {
                return List.of();
            }

            public void afterItems(ServerPlayer player, Reward reward) {
                String command = reward.target()
                        .strip()
                        .replace("{player}", player.getGameProfile().name());
                var server = player.level().getServer();
                var source = player.createCommandSourceStack()
                        .withPermission(LevelBasedPermissionSet.OWNER)
                        .withSuppressedOutput();
                for (int i = 0; i < reward.count(); i++) {
                    try {
                        server.getCommands().performPrefixedCommand(source, command);
                    } catch (RuntimeException error) {
                        IWantQuests.LOGGER.error("Reward command '{}' failed", command, error);
                    }
                }
            }
        });
    }

    public static void register(String type, Provider provider) {
        if (TYPES.putIfAbsent(type, provider) != null)
            throw new IllegalArgumentException("Duplicate reward provider: " + type);
    }

    public static Provider get(String type) {
        Provider provider = TYPES.get(type);
        if (provider == null) throw new IllegalArgumentException("Unknown reward provider: " + type);
        return provider;
    }

    private RewardRegistry() {}
}
