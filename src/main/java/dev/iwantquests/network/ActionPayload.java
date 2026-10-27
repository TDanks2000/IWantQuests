package dev.iwantquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Contains only an action and identifiers, never quantities or progression. */
public record ActionPayload(String action, String quest, String objective) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iwantquests", "action"));
    public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            ActionPayload::action,
            ByteBufCodecs.stringUtf8(256),
            ActionPayload::quest,
            ByteBufCodecs.stringUtf8(64),
            ActionPayload::objective,
            ActionPayload::new);

    @Override
    public Type<ActionPayload> type() {
        return TYPE;
    }
}
