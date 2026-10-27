package dev.iwantquests.network;

/** What the editor client learns about its last request. {@code select} names a newly created quest or chapter. */
public record EditResult(boolean ok, String message, String select) {}
