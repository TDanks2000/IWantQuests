package dev.iwantquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.iwantquests.quest.QuestLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.*;

public final class QuestSavedData extends SavedData {
    public static final SavedDataType<QuestSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("iwantquests", "progress"),
            QuestSavedData::new,
            Codec.STRING.comapFlatMap(QuestSavedData::decode, data -> QuestLoader.JSON.toJson(data.state)),
            null);
    private final ProgressState state;

    public QuestSavedData() {
        this(new ProgressState());
    }

    private QuestSavedData(ProgressState state) {
        this.state = state;
    }

    public ProgressState state() {
        return state;
    }

    private static DataResult<QuestSavedData> decode(String json) {
        try {
            ProgressState state = QuestLoader.JSON.fromJson(json, ProgressState.class);
            if (state == null
                    || state.version != 1
                    || state.teams == null
                    || state.membership == null
                    || state.soloTeams == null
                    || state.claims == null
                    || state.invitations == null)
                throw new IllegalArgumentException("Unsupported or corrupted IWantQuests save");
            if (state.names == null) state.names = new java.util.HashMap<>();
            return DataResult.success(new QuestSavedData(state));
        } catch (RuntimeException e) {
            return DataResult.error(() -> e.getMessage());
        }
    }
}
