package dev.iwantquests.reward;

import dev.iwantquests.quest.QuestPack.Reward;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Turns a quest's reward list into the concrete rewards one claim delivers: random rewards are rolled and
 * choice rewards use the option the player picked. Free of Minecraft classes so it can be tested directly.
 */
public final class RewardResolver {
    /** The number of choice rewards in {@code rewards}, which is how many picks a claim must carry. */
    public static int choiceCount(List<Reward> rewards) {
        return (int) rewards.stream().filter(r -> r.type().equals("choice")).count();
    }

    /**
     * @param roll returns a uniformly random number in {@code [0, bound)}
     * @param choices option indices for the choice rewards, in the order those rewards appear
     * @throws IllegalArgumentException when a pick is missing or out of range
     */
    public static List<Reward> resolve(List<Reward> rewards, IntUnaryOperator roll, List<Integer> choices) {
        List<Reward> result = new ArrayList<>();
        int pick = 0;
        for (Reward reward : rewards) {
            switch (reward.type()) {
                case "random" -> {
                    for (int i = 0; i < Math.max(1, reward.count()); i++)
                        result.add(weighted(reward.optionsOrEmpty(), roll));
                }
                case "choice" -> {
                    List<Reward> options = reward.optionsOrEmpty();
                    if (pick >= choices.size()) throw new IllegalArgumentException("Choose a reward first.");
                    int index = choices.get(pick++);
                    if (index < 0 || index >= options.size())
                        throw new IllegalArgumentException("That reward choice does not exist.");
                    result.add(options.get(index));
                }
                default -> result.add(reward);
            }
        }
        if (pick != choices.size()) throw new IllegalArgumentException("Unexpected reward choices.");
        return result;
    }

    private static Reward weighted(List<Reward> options, IntUnaryOperator roll) {
        int total = 0;
        for (Reward option : options) total += option.weightOrDefault();
        int value = roll.applyAsInt(total);
        for (Reward option : options) {
            value -= option.weightOrDefault();
            if (value < 0) return option;
        }
        return options.getLast();
    }

    /** Parses the comma-separated option numbers a claim request carries. An empty text means no picks. */
    public static List<Integer> parseChoices(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            try {
                result.add(Integer.parseInt(part.strip()));
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid reward choice.");
            }
            if (result.size() > 32) throw new IllegalArgumentException("Too many reward choices.");
        }
        return List.copyOf(result);
    }

    private RewardResolver() {}
}
