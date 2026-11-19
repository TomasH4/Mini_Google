package com.minigoogle.query;

/**
 * Represents a single term (word) in the query.
 */
public record TermNode(String word) implements QueryNode {}
