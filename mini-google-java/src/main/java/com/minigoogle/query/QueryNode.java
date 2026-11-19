package com.minigoogle.query;

/**
 * Base type for the abstract syntax tree of a search query.
 *
 * Each concrete node type (TermNode, PhraseNode, AndNode, OrNode, NotNode)
 * is defined in its own file and is listed in the {@code permits}
 * clause so the compiler knows the complete hierarchy.
 */
public sealed interface QueryNode permits TermNode, PhraseNode, AndNode, OrNode, NotNode {
}
