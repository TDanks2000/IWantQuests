package dev.iwantquests.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits large JSON messages into parts for the network and joins them again on arrival, so a catalogue is
 * never limited by the 1 MiB packet size. Payloads of one connection arrive in order. Free of Minecraft classes.
 */
public final class SyncAssembler {
    /** Characters per packet. A character is at most three UTF-8 bytes, so a packet stays far below 1 MiB. */
    public static final int CHUNK = 120_000;
    /** Upper bound on one message, so a broken peer cannot make the client buffer without limit. */
    public static final int MAX_PARTS = 256;

    /** One packet's worth of a message. A message sent whole has {@code part == 0} and {@code parts == 1}. */
    public record Part(String kind, String json, int part, int parts) {
        public Part(String kind, String json) {
            this(kind, json, 0, 1);
        }
    }

    /** The parts that carry {@code json}, in the order they must be sent. A short message is a single part. */
    public static List<Part> split(String kind, String json) {
        if (json.length() <= CHUNK) return List.of(new Part(kind, json));
        int parts = (json.length() + CHUNK - 1) / CHUNK;
        if (parts > MAX_PARTS) throw new IllegalArgumentException("Quest data is too large to synchronise.");
        List<Part> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++)
            result.add(new Part(kind, json.substring(i * CHUNK, Math.min(json.length(), (i + 1) * CHUNK)), i, parts));
        return result;
    }

    private StringBuilder buffer;
    private String kind;
    private int nextPart, parts;

    /** Returns the complete message, or null while more parts are expected or when the sequence is invalid. */
    public Part accept(Part incoming) {
        if (incoming.parts() <= 1) {
            reset();
            return incoming.part() == 0 ? incoming : null;
        }
        if (incoming.parts() > MAX_PARTS || incoming.part() < 0 || incoming.part() >= incoming.parts()) {
            reset();
            return null;
        }
        if (incoming.part() == 0) {
            reset();
            buffer = new StringBuilder(incoming.json().length() * incoming.parts());
            kind = incoming.kind();
            parts = incoming.parts();
        } else if (buffer == null
                || incoming.part() != nextPart
                || incoming.parts() != parts
                || !incoming.kind().equals(kind)) {
            reset();
            return null;
        }
        buffer.append(incoming.json());
        nextPart = incoming.part() + 1;
        if (nextPart < parts) return null;
        Part whole = new Part(kind, buffer.toString());
        reset();
        return whole;
    }

    public void reset() {
        buffer = null;
        kind = null;
        nextPart = 0;
        parts = 0;
    }
}
