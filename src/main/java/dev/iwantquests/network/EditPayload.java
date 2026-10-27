package dev.iwantquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A quest-editor request: operation name and bounded JSON body. The server checks enabled creator access
 * before every request. Edits affect private drafts; publishing alone changes the live quest book.
 */
public record EditPayload(String op, String json) implements CustomPacketPayload {
    public static final int MAX_JSON = 65_536;
    public static final Type<EditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iwantquests", "edit"));
    public static final StreamCodec<ByteBuf, EditPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            EditPayload::op,
            ByteBufCodecs.stringUtf8(MAX_JSON),
            EditPayload::json,
            EditPayload::new);

    @Override
    public Type<EditPayload> type() {
        return TYPE;
    }
}
