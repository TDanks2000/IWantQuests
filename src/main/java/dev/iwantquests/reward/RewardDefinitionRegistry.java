package dev.iwantquests.reward;

import dev.iwantquests.quest.QuestPack.Reward;
import java.util.*;
import java.util.function.BiConsumer;

/** Domain validation is separate from Minecraft reward delivery so it can run in plain JVM tests. */
public final class RewardDefinitionRegistry {
    @FunctionalInterface
    public interface Validator {
        void validate(Reward reward, BiConsumer<String, String> targets);
    }
    /** Reward types that hold other rewards and so cannot be nested inside each other. */
    public static final Set<String> CONTAINERS = Set.of("random", "choice");

    public static final int MAX_COMMAND_LENGTH = 512, MAX_OPTIONS = 32, MAX_NAME_LENGTH = 100;
    private static final Map<String, Validator> TYPES = new LinkedHashMap<>();

    static {
        register("item", (reward, targets) -> {
            positive(reward, 1_000_000);
            targets.accept("item", reward.target());
        });
        register("xp", (reward, targets) -> positive(reward, 1_000_000));
        register("command", (reward, targets) -> {
            positive(reward, 100);
            String command = reward.target();
            if (command == null || command.isBlank() || command.strip().equals("/"))
                throw new IllegalArgumentException("Command reward needs a command");
            if (command.length() > MAX_COMMAND_LENGTH || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
                throw new IllegalArgumentException(
                        "Command reward must be one line of at most " + MAX_COMMAND_LENGTH + " characters");
        });
        register("loot_table", (reward, targets) -> {
            positive(reward, 64);
            if (reward.target() == null || !reward.target().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException(
                        "Loot table reward needs a loot table ID such as minecraft:chests/simple_dungeon");
        });
        register("random", (reward, targets) -> {
            positive(reward, 16);
            options(reward, targets);
        });
        register("choice", (reward, targets) -> {
            if (reward.count() > 1)
                throw new IllegalArgumentException("A choice reward gives one option; count must be 1 or absent");
            options(reward, targets);
        });
    }

    public static void register(String id, Validator validator) {
        if (TYPES.putIfAbsent(id, validator) != null)
            throw new IllegalArgumentException("Duplicate reward type: " + id);
    }
    /** Registered type IDs in registration order; the editor offers these. */
    public static List<String> types() {
        return List.copyOf(TYPES.keySet());
    }

    public static void validate(Reward reward, BiConsumer<String, String> targets) {
        Validator validator = TYPES.get(reward.type());
        if (validator == null) throw new IllegalArgumentException("Unknown reward type: " + reward.type());
        if (reward.name() != null && reward.name().length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException("Reward name exceeds " + MAX_NAME_LENGTH + " characters");
        if (reward.icon() != null) targets.accept("item", reward.icon());
        if (reward.weight() != null && (reward.weight() < 1 || reward.weight() > 1000))
            throw new IllegalArgumentException("Reward weight must be between 1 and 1000");
        if (!CONTAINERS.contains(reward.type()) && reward.options() != null)
            throw new IllegalArgumentException(reward.type() + " rewards cannot have options");
        validator.validate(reward, targets);
    }

    private static void positive(Reward reward, int max) {
        if (reward.count() < 1 || reward.count() > max)
            throw new IllegalArgumentException(reward.type() + " reward count must be between 1 and " + max);
    }

    private static void options(Reward reward, BiConsumer<String, String> targets) {
        List<Reward> options = reward.optionsOrEmpty();
        if (options.isEmpty() || options.size() > MAX_OPTIONS)
            throw new IllegalArgumentException(reward.type() + " reward needs 1 to " + MAX_OPTIONS + " options");
        for (Reward option : options) {
            if (option == null || option.type() == null)
                throw new IllegalArgumentException(reward.type() + " reward has an empty option");
            if (CONTAINERS.contains(option.type()))
                throw new IllegalArgumentException(
                        reward.type() + " rewards cannot contain " + option.type() + " rewards");
            validate(option, targets);
        }
    }

    private RewardDefinitionRegistry() {}
}
