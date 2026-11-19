package com.minigoogle.model;

import java.util.List;

/**
 * Represents an entry in the inverted index for a single term.
 */
public record Posting(int docId, List<Integer> positions) {
    public Posting {
        if (positions == null) {
            throw new IllegalArgumentException("positions cannot be null");
        }
    }
}
