package com.minigoogle.query;

/**
 * Logical NOT of a sub‑query.
 */
public record NotNode(QueryNode child) implements QueryNode {}
