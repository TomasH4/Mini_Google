package com.minigoogle.query;

/**
 * Logical OR of two sub‑queries.
 */
public record OrNode(QueryNode left, QueryNode right) implements QueryNode {}
