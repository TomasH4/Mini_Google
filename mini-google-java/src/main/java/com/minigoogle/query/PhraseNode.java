package com.minigoogle.query;

import java.util.List;

/**
 * Represents a phrase consisting of several ordered words.
 */
public record PhraseNode(List<String> words) implements QueryNode {}
