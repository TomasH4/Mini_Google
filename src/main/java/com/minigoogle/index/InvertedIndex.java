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

    /** Adds a document to the index, tokenizing its body and grouping positions by term. */
    public void addDocument(Document doc) {
        documents.put(doc.id(), doc);

        // Group token positions per term for this specific document
        String[] tokens = doc.body().toLowerCase().split("\\s+");
        Map<String, List<Integer>> termPositions = new HashMap<>();

        for (int pos = 0; pos < tokens.length; pos++) {
            String term = cleanTerm(tokens[pos]);
            if (term.isEmpty()) continue;
            termPositions.computeIfAbsent(term, k -> new ArrayList<>()).add(pos);
        }

        // Add one posting per term for this document (ordered positions)
        termPositions.forEach((term, positions) -> {
            index.computeIfAbsent(term, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(new Posting(doc.id(), List.copyOf(positions)));
        });

        // Update average document length (thread-safe update)
        synchronized (this) {
            int totalDocs = documents.size();
            avgDocLength = ((avgDocLength * (totalDocs - 1)) + doc.length()) / totalDocs;
        }
    }

    private static String cleanTerm(String raw) {
        return raw.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
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

    public int getDocumentCount() {
        return documents.size();
    }

    public int getVocabularySize() {
        return index.size();
    }
}
