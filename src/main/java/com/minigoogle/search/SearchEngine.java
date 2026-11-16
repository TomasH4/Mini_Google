package com.minigoogle.search;

import com.minigoogle.index.InvertedIndex;
import com.minigoogle.model.Document;
import com.minigoogle.model.SearchResult;
import com.minigoogle.model.Posting;
import com.minigoogle.query.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core search engine that evaluates a QueryNode against an InvertedIndex,
 * calculates BM25 scores and returns the top‑K results.
 */
public class SearchEngine {
    private final InvertedIndex index;
    private final double k1 = 1.5; // typical value
    private final double b = 0.75; // typical value
    private final int topK = 10;

    public SearchEngine(InvertedIndex index) {
        this.index = index;
    }

    /** Public entry point */
    public List<SearchResult> search(String queryString) {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize(queryString);
        QueryNode ast = new QueryParser(tokens).parse();
        Set<Integer> docIds = evaluate(ast);
        return rank(docIds, ast);
    }

    /** Recursively evaluate the AST and return the matching document IDs */
    private Set<Integer> evaluate(QueryNode node) {
        return switch (node) {
            case TermNode tn -> evaluateTerm(tn.word());
            case PhraseNode pn -> evaluatePhrase(pn.words());
            case AndNode an -> {
                Set<Integer> left = evaluate(an.left());
                Set<Integer> right = evaluate(an.right());
                left.retainAll(right);
                yield left;
            }
            case OrNode on -> {
                Set<Integer> left = evaluate(on.left());
                Set<Integer> right = evaluate(on.right());
                left.addAll(right);
                yield left;
            }
            case NotNode nn -> {
                Set<Integer> all = new HashSet<>(index.allDocuments().stream()
                                              .map(Document::id).collect(Collectors.toSet()));
                Set<Integer> child = evaluate(nn.child());
                all.removeAll(child);
                yield all;
            }
        };
    }

    private Set<Integer> evaluateTerm(String term) {
        return index.getPostings(term).stream()
                    .map(Posting::docId)
                    .collect(Collectors.toSet());
    }

    /** Simple phrase evaluation: intersect documents containing all terms and then verify positions */
    private Set<Integer> evaluatePhrase(List<String> words) {
        if (words.isEmpty()) return Set.of();
        // first get candidate docIds that contain all terms
        Set<Integer> candidates = new HashSet<>(evaluateTerm(words.get(0)));
        for (int i = 1; i < words.size(); i++) {
            candidates.retainAll(evaluateTerm(words.get(i)));
            if (candidates.isEmpty()) break;
        }
        // verify positional adjacency
        Set<Integer> result = new HashSet<>();
        for (int docId : candidates) {
            List<List<Integer>> positionsLists = new ArrayList<>();
            for (String w : words) {
                List<Integer> pos = index.getPostings(w).stream()
                                          .filter(p -> p.docId() == docId)
                                          .flatMap(p -> p.positions().stream())
                                          .sorted()
                                          .collect(Collectors.toList());
                positionsLists.add(pos);
            }
            // check if there exists a position p in first list such that p+1 in second, p+2 in third, ...
            for (int start : positionsLists.get(0)) {
                boolean ok = true;
                for (int i = 1; i < positionsLists.size(); i++) {
                    if (!positionsLists.get(i).contains(start + i)) { ok = false; break; }
                }
                if (ok) { result.add(docId); break; }
            }
        }
        return result;
    }

    /** Rank the given doc IDs using BM25 and keep top‑K */
    private List<SearchResult> rank(Set<Integer> docIds, QueryNode query) {
        // collect all query terms (flatten) – for simplicity we only weight terms, not phrases
        List<String> terms = collectTerms(query);
        Map<Integer, Double> scores = new HashMap<>();
        double avgdl = index.getAvgDocLength();
        int N = index.allDocuments().size();
        for (int docId : docIds) {
            double sum = 0.0;
            Document doc = index.getDocument(docId);
            int docLen = doc.length();
            for (String term : terms) {
                int f = termFrequency(term, docId);
                if (f == 0) continue;
                int n = index.getPostings(term).size(); // number of docs containing term
                double idf = Math.log((N - n + 0.5) / (n + 0.5) + 1.0);
                double numerator = f * (k1 + 1);
                double denominator = f + k1 * (1 - b + b * docLen / avgdl);
                sum += idf * (numerator / denominator);
            }
            scores.put(docId, sum);
        }
        // min‑heap of size topK
        PriorityQueue<SearchResult> heap = new PriorityQueue<>(Comparator.comparingDouble(sr -> sr.score()));
        for (Map.Entry<Integer, Double> e : scores.entrySet()) {
            Document d = index.getDocument(e.getKey());
            SearchResult sr = new SearchResult(e.getKey(), d.title(), e.getValue());
            if (heap.size() < topK) {
                heap.offer(sr);
            } else if (e.getValue() > heap.peek().score()) {
                heap.poll();
                heap.offer(sr);
            }
        }
        List<SearchResult> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return result;
    }

    private int termFrequency(String term, int docId) {
        return index.getPostings(term).stream()
                    .filter(p -> p.docId() == docId)
                    .flatMap(p -> p.positions().stream())
                    .mapToInt(i -> 1).sum();
    }

    /** Collect all term strings from the AST (terms inside phrases are also counted) */
    private List<String> collectTerms(QueryNode node) {
        List<String> list = new ArrayList<>();
        switch (node) {
            case TermNode tn -> list.add(tn.word());
            case PhraseNode pn -> list.addAll(pn.words());
            case AndNode an -> { list.addAll(collectTerms(an.left())); list.addAll(collectTerms(an.right())); }
            case OrNode on -> { list.addAll(collectTerms(on.left())); list.addAll(collectTerms(on.right())); }
            case NotNode nn -> list.addAll(collectTerms(nn.child()));
        }
        return list;
    }
}
