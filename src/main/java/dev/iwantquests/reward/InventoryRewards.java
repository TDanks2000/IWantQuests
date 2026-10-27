package dev.iwantquests.reward;

import dev.iwantquests.engine.QuestEngine.RewardDelivery;
import dev.iwantquests.quest.QuestPack.Reward;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryRewards implements RewardDelivery {
    private final ServerPlayer player;
    private final List<Integer> choices;
    private List<ItemStack> prepared;
    private List<Reward> resolved;

    public InventoryRewards(ServerPlayer player) {
        this(player, List.of());
    }
    /** {@code choices} are the option numbers the player picked for the quest's choice rewards, in order. */
    public InventoryRewards(ServerPlayer player, List<Integer> choices) {
        this.player = player;
        this.choices = choices;
    }

    @Override
    public boolean canDeliver(List<Reward> rewards) {
        prepared = null;
        resolved = null;
        List<Reward> leaves =
                RewardResolver.resolve(rewards, bound -> player.getRandom().nextInt(bound), choices);
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems().stream()
                .map(ItemStack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (Reward reward : leaves)
            for (ItemStack rewardStack : RewardRegistry.get(reward.type()).items(player, reward)) {
                int remaining = rewardStack.getCount();
                for (ItemStack slot : inventory) {
                    if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rewardStack)) {
                        int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                        slot.grow(moved);
                        remaining -= moved;
                    }
                }
                for (int i = 0; i < inventory.size() && remaining > 0; i++)
                    if (inventory.get(i).isEmpty()) {
                        int moved = Math.min(remaining, rewardStack.getMaxStackSize());
                        inventory.set(i, rewardStack.copyWithCount(moved));
                        remaining -= moved;
                    }
                if (remaining > 0) return false;
            }
        prepared = inventory;
        resolved = leaves;
        return true;
    }

    @Override
    public void deliver(List<Reward> rewards) {
        if (prepared == null || resolved == null) throw new IllegalStateException("Rewards were not prepared");
        for (int i = 0; i < prepared.size(); i++) player.getInventory().setItem(i, prepared.get(i));
        for (Reward reward : resolved) RewardRegistry.get(reward.type()).afterItems(player, reward);
        player.inventoryMenu.broadcastChanges();
    }
}
