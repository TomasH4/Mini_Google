package com.minigoogle.query;

/**
 * Logical AND of two sub‑queries.
 */
public record AndNode(QueryNode left, QueryNode right) implements QueryNode {}
