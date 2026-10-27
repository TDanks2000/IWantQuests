package dev.iwantquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One server-to-client message, or one part of a large one; see {@link SyncAssembler}.
 */
public record SyncPayload(String kind, String json, int part, int parts) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iwantquests", "sync"));
    public static final StreamCodec<ByteBuf, SyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32),
            SyncPayload::kind,
            ByteBufCodecs.stringUtf8(SyncAssembler.CHUNK),
            SyncPayload::json,
            ByteBufCodecs.VAR_INT,
            SyncPayload::part,
            ByteBufCodecs.VAR_INT,
            SyncPayload::parts,
            SyncPayload::new);

    public SyncPayload(String kind, String json) {
        this(kind, json, 0, 1);
    }

    /** The payloads that carry {@code json}, in the order they must be sent. A short document is one payload. */
    public static List<SyncPayload> split(String kind, String json) {
        return SyncAssembler.split(kind, json).stream()
                .map(p -> new SyncPayload(p.kind(), p.json(), p.part(), p.parts()))
                .toList();
    }

    public SyncAssembler.Part toPart() {
        return new SyncAssembler.Part(kind, json, part, parts);
    }

    @Override
    public Type<SyncPayload> type() {
        return TYPE;
    }
}
