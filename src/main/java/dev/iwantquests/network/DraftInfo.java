package dev.iwantquests.network;

import dev.iwantquests.edit.QuestDraft.Review;

/** Private authoring metadata; never sent to players without authoring permission. */
public record DraftInfo(Review review) {}
