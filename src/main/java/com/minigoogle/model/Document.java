package com.minigoogle.model;

/**
 * Immutable representation of a document.
 */
public record Document(int id, String title, String body, int length) {
    public Document {
        if (title == null || body == null) {
            throw new IllegalArgumentException("title and body cannot be null");
        }
    }
}
