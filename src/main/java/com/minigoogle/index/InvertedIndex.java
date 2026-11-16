package com.minigoogle.index;

import com.minigoogle.model.Document;
import com.minigoogle.model.Posting;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central inverted index structure.
 * Maintains a mapping from term -> list of postings, and a document store.
 */
public class InvertedIndex {
    private final Map<String, List<Posting>> index = new ConcurrentHashMap<>();
    private final Map<Integer, Document> documents = new ConcurrentHashMap<>();
    private double avgDocLength = 0.0;

    /** Adds a document to the index, tokenizing its body. */
    public void addDocument(Document doc) {
        // Store document
        documents.put(doc.id(), doc);
        // Tokenize body (simple whitespace + lowercasing) and record positions
        String[] tokens = doc.body().toLowerCase().split("\\s+");
        for (int pos = 0; pos < tokens.length; pos++) {
            String term = tokens[pos];
            // Remove leading/trailing punctuation (non‑letter/number)
            term = term.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            if (term.isEmpty()) continue;
            index.computeIfAbsent(term, k -> new ArrayList<>())
                 .add(new Posting(doc.id(), List.of(pos)));
        }
        // Update average document length (incremental)
        int totalDocs = documents.size();
        avgDocLength = ((avgDocLength * (totalDocs - 1)) + doc.length()) / totalDocs;
    }

    /** Returns the list of postings for a term, or empty list if term not present. */
    public List<Posting> getPostings(String term) {
        return index.getOrDefault(term.toLowerCase(), Collections.emptyList());
    }

    public Document getDocument(int docId) {
        return documents.get(docId);
    }

    public Collection<Document> allDocuments() {
        return documents.values();
    }

    public double getAvgDocLength() {
        return avgDocLength;
    }
}
