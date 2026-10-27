package dev.iwantquests.network;

import static org.junit.jupiter.api.Assertions.*;

import dev.iwantquests.network.SyncAssembler.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncAssemblerTest {
    private static String text(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) text.append((char) ('a' + i % 26));
        return text.toString();
    }

    private static Part send(SyncAssembler assembler, List<Part> parts) {
        Part whole = null;
        for (Part part : parts) whole = assembler.accept(part);
        return whole;
    }

    @Test
    void shortMessageIsOnePart() {
        List<Part> parts = SyncAssembler.split("state", "{}");
        assertEquals(1, parts.size());
        assertEquals("{}", new SyncAssembler().accept(parts.getFirst()).json());
    }

    @Test
    void largeMessageSurvivesTheRoundTrip() {
        String json = text(SyncAssembler.CHUNK * 5 + 17);
        List<Part> parts = SyncAssembler.split("definitions", json);
        assertEquals(6, parts.size());
        assertTrue(parts.stream().allMatch(p -> p.json().length() <= SyncAssembler.CHUNK));
        Part whole = send(new SyncAssembler(), parts);
        assertEquals("definitions", whole.kind());
        assertEquals(json, whole.json());
    }

    @Test
    void messageOfExactlyOneChunkIsNotSplit() {
        assertEquals(1, SyncAssembler.split("state", text(SyncAssembler.CHUNK)).size());
        assertEquals(
                2, SyncAssembler.split("state", text(SyncAssembler.CHUNK + 1)).size());
    }

    @Test
    void partsOfTwoMessagesInSequenceAreKeptApart() {
        SyncAssembler assembler = new SyncAssembler();
        String first = text(SyncAssembler.CHUNK * 2 + 1), second = text(SyncAssembler.CHUNK + 5);
        assertEquals(
                first,
                send(assembler, SyncAssembler.split("definitions", first)).json());
        assertEquals(
                second,
                send(assembler, SyncAssembler.split("draft_definitions", second))
                        .json());
    }

    @Test
    void smallMessageBetweenPartsDropsTheIncompleteOne() {
        SyncAssembler assembler = new SyncAssembler();
        List<Part> parts = SyncAssembler.split("definitions", text(SyncAssembler.CHUNK * 3));
        assertNull(assembler.accept(parts.get(0)));
        assertEquals("ok", assembler.accept(new Part("result", "ok")).json());
        assertNull(assembler.accept(parts.get(1)), "a stray part must not be joined to anything");
        assertNull(assembler.accept(parts.get(2)));
    }

    @Test
    void missingOrRepeatedPartInvalidatesTheMessage() {
        List<Part> parts = SyncAssembler.split("definitions", text(SyncAssembler.CHUNK * 3));
        SyncAssembler skipped = new SyncAssembler();
        assertNull(skipped.accept(parts.get(0)));
        assertNull(skipped.accept(parts.get(2)));
        SyncAssembler repeated = new SyncAssembler();
        assertNull(repeated.accept(parts.get(0)));
        assertNull(repeated.accept(parts.get(1)));
        assertNull(repeated.accept(parts.get(1)), "a repeated part invalidates the message");
        assertNull(repeated.accept(parts.get(2)));
        assertNull(send(new SyncAssembler(), List.of(parts.get(1), parts.get(2))));
    }

    @Test
    void restartingAMessageDiscardsTheOldOne() {
        SyncAssembler assembler = new SyncAssembler();
        String json = text(SyncAssembler.CHUNK * 2);
        assertNull(assembler.accept(SyncAssembler.split("definitions", text(SyncAssembler.CHUNK * 4))
                .getFirst()));
        assertEquals(
                json, send(assembler, SyncAssembler.split("definitions", json)).json());
    }

    @Test
    void absurdPartCountsAreRejected() {
        SyncAssembler assembler = new SyncAssembler();
        assertNull(assembler.accept(new Part("definitions", "x", 0, SyncAssembler.MAX_PARTS + 1)));
        assertNull(assembler.accept(new Part("definitions", "x", 5, 3)));
        assertNull(assembler.accept(new Part("definitions", "x", -1, 3)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SyncAssembler.split("definitions", text(SyncAssembler.CHUNK * (SyncAssembler.MAX_PARTS + 1))));
    }
}
