package dev.iwantquests.objective;

import dev.iwantquests.quest.QuestPack.Objective;
import java.util.*;

/** Register additional types during mod construction, before the first resource reload. */
public final class ObjectiveRegistry {
    public interface Type {
        void validate(Objective objective);

        boolean matches(Objective objective, Contribution event);
    }

    public record Contribution(
            String type, Set<String> targets, int count, String dimension, double x, double y, double z) {
        public static Contribution item(String type, Set<String> targets, int count) {
            return new Contribution(type, targets, count, "", 0, 0, 0);
        }
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    static {
        for (String name : List.of("obtain", "possess", "craft", "kill", "submit", "manual", "dimension")) {
            register(name, new Type() {
                public void validate(Objective o) {
                    if (!name.equals("manual")
                            && (o.target() == null || o.target().isBlank()))
                        throw new IllegalArgumentException("Missing target for " + name);
                }

                public boolean matches(Objective o, Contribution e) {
                    return name.equals("manual")
                            ? e.targets().contains(o.id())
                            : e.targets().contains(o.target());
                }
            });
        }
        register("region", new Type() {
            public void validate(Objective o) {
                if (o.dimension() == null
                        || o.dimension().isBlank()
                        || o.minX() > o.maxX()
                        || o.minY() > o.maxY()
                        || o.minZ() > o.maxZ())
                    throw new IllegalArgumentException("Invalid region bounds or dimension");
            }

            public boolean matches(Objective o, Contribution e) {
                return o.dimension().equals(e.dimension())
                        && e.x() >= o.minX()
                        && e.x() <= o.maxX()
                        && e.y() >= o.minY()
                        && e.y() <= o.maxY()
                        && e.z() >= o.minZ()
                        && e.z() <= o.maxZ();
            }
        });
    }

    public static void register(String id, Type type) {
        if (TYPES.putIfAbsent(id, type) != null) throw new IllegalArgumentException("Duplicate objective type: " + id);
    }
    /** Registered type IDs in registration order; the editor offers these. */
    public static List<String> types() {
        return List.copyOf(TYPES.keySet());
    }

    public static Type get(String id) {
        Type type = TYPES.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown objective type: " + id);
        return type;
    }

    private ObjectiveRegistry() {}
}
