package com.minigoogle.model;

/**
 * Result of a search query.
 */
public record SearchResult(int docId, String title, double score) {}
