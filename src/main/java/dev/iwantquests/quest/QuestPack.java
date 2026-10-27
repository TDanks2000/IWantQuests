package dev.iwantquests.quest;

import java.util.List;

public record QuestPack(List<Chapter> chapters, List<Quest> quests) {
    /**
     * The pack as ordinary players receive it: command rewards keep their name and icon but not the command text,
     * since the server alone runs commands. Returns this pack when there is nothing to hide.
     */
    public QuestPack withoutCommands() {
        if (quests.stream()
                .noneMatch(q -> q.rewards() != null && q.rewards().stream().anyMatch(Reward::hasCommand))) return this;
        return new QuestPack(
                chapters,
                quests.stream()
                        .map(q -> q.rewards() == null
                                ? q
                                : q.withRewards(q.rewards().stream()
                                        .map(Reward::withoutCommands)
                                        .toList()))
                        .toList());
    }
    /**
     * Presentation fields ({@code subtitle}, {@code theme}, {@code background}, {@code prerequisites})
     * are optional; Gson leaves absent fields null.
     */
    public record Chapter(
            String id,
            String title,
            String icon,
            int order,
            String subtitle,
            String theme,
            String background,
            List<String> prerequisites) {
        public List<String> prerequisitesOrEmpty() {
            return prerequisites == null ? List.of() : prerequisites;
        }
    }
    /**
     * {@code icon} is an item ID; {@code iconTexture} is an optional GUI sprite ID shown instead of the item.
     * {@code shape} is {@code task}, {@code goal}, {@code challenge}, or a theme-defined shape ID; absent means task.
     * {@code size} scales the node (0.5-3.0); 0 (absent) means 1.
     */
    public record Quest(
            String id,
            String chapter,
            String title,
            String description,
            String icon,
            double x,
            double y,
            List<String> prerequisites,
            List<Objective> objectives,
            List<Reward> rewards,
            boolean optional,
            boolean hidden,
            boolean retroactive,
            boolean repeatable,
            String subtitle,
            String shape,
            double size,
            String iconTexture) {
        public Quest withRewards(List<Reward> rewards) {
            return new Quest(
                    id,
                    chapter,
                    title,
                    description,
                    icon,
                    x,
                    y,
                    prerequisites,
                    objectives,
                    rewards,
                    optional,
                    hidden,
                    retroactive,
                    repeatable,
                    subtitle,
                    shape,
                    size,
                    iconTexture);
        }

        public String shapeOrDefault() {
            return shape == null || shape.isBlank() ? "task" : shape;
        }

        public double sizeOrDefault() {
            return size == 0 ? 1 : size;
        }
    }

    public record Objective(
            String id,
            String type,
            String target,
            int count,
            String description,
            String dimension,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        /** An objective that only needs a type, a target and a count; region bounds stay empty. */
        public static Objective simple(String id, String type, String target, int count, String description) {
            return new Objective(id, type, target, count, description, null, 0, 0, 0, 0, 0, 0);
        }

        public String signature() {
            return type + "|" + target + "|" + dimension + "|" + minX + "|" + minY + "|" + minZ + "|" + maxX + "|"
                    + maxY + "|" + maxZ;
        }
    }
    /**
     * {@code name} and {@code icon} are optional display overrides. {@code options} and {@code weight} are used by
     * {@code random} and {@code choice} rewards: each option is itself a reward, and {@code weight} (default 1)
     * is its share of a random pick.
     */
    public record Reward(
            String type, String target, int count, String name, String icon, Integer weight, List<Reward> options) {
        public Reward(String type, String target, int count) {
            this(type, target, count, null, null, null, null);
        }

        public int weightOrDefault() {
            return weight == null ? 1 : weight;
        }

        public List<Reward> optionsOrEmpty() {
            return options == null ? List.of() : options;
        }

        public Reward withType(String type) {
            return new Reward(type, target, count, name, icon, weight, options);
        }

        public Reward withTarget(String target) {
            return new Reward(type, target, count, name, icon, weight, options);
        }

        public Reward withCount(int count) {
            return new Reward(type, target, count, name, icon, weight, options);
        }
        /** True for a command reward, or a reward holding one. */
        public boolean hasCommand() {
            return "command".equals(type) || optionsOrEmpty().stream().anyMatch(Reward::hasCommand);
        }

        public Reward withoutCommands() {
            if (!hasCommand()) return this;
            return new Reward(
                    type,
                    "command".equals(type) ? "" : target,
                    count,
                    name,
                    icon,
                    weight,
                    options == null
                            ? null
                            : options.stream().map(Reward::withoutCommands).toList());
        }
    }
}
