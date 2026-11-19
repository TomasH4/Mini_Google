package com.minigoogle.search;

import com.minigoogle.index.InvertedIndex;
import com.minigoogle.model.Document;
import com.minigoogle.model.Posting;
import com.minigoogle.model.SearchResult;
import com.minigoogle.query.*;

import java.util.*;

/**
 * High-performance core search engine.
 * Evaluates QueryNode AST against InvertedIndex in O(N) linear time,
 * applies BM25 ranking directly over postings, and selects top-K via Min-Heap.
 */
public class SearchEngine {
    private final InvertedIndex index;
    private final double k1 = 1.5;
    private final double b = 0.75;
    private final int topK = 10;

    public SearchEngine(InvertedIndex index) {
        this.index = index;
    }

    /** Public entry point */
    public List<SearchResult> search(String queryString) {
        List<Tokenizer.Token> tokens = Tokenizer.tokenize(queryString);
        QueryNode ast = new QueryParser(tokens).parse();
        Set<Integer> docIds = evaluate(ast);
        if (docIds.isEmpty()) {
            return Collections.emptyList();
        }
        return rank(docIds, ast);
    }

    /** Recursively evaluate the AST and return matching document IDs */
    private Set<Integer> evaluate(QueryNode node) {
        return switch (node) {
            case TermNode tn -> evaluateTerm(tn.word());
            case PhraseNode pn -> evaluatePhrase(pn.words());
            case AndNode an -> {
                Set<Integer> left = evaluate(an.left());
                if (left.isEmpty()) yield Collections.emptySet();
                Set<Integer> right = evaluate(an.right());
                if (right.isEmpty()) yield Collections.emptySet();
                Set<Integer> result = new HashSet<>(left);
                result.retainAll(right);
                yield result;
            }
            case OrNode on -> {
                Set<Integer> left = evaluate(on.left());
                Set<Integer> right = evaluate(on.right());
                if (left.isEmpty()) yield right;
                if (right.isEmpty()) yield left;
                Set<Integer> result = new HashSet<>(left);
                result.addAll(right);
                yield result;
            }
            case NotNode nn -> {
                Set<Integer> all = new HashSet<>(index.allDocIds());
                Set<Integer> excluded = evaluate(nn.child());
                all.removeAll(excluded);
                yield all;
            }
        };
    }

    private Set<Integer> evaluateTerm(String term) {
        List<Posting> postings = index.getPostings(term);
        Set<Integer> docIds = new HashSet<>(Math.max(16, postings.size() * 4 / 3 + 1));
        for (Posting p : postings) {
            docIds.add(p.docId());
        }
        return docIds;
    }

    /**
     * O(N) phrase evaluation:
     * 1. Intersects candidates by document ID.
     * 2. Maps (docId -> positions) in O(1) hash maps instead of scanning linear postings.
     * 3. Checks positional adjacency.
     */
    private Set<Integer> evaluatePhrase(List<String> words) {
        if (words.isEmpty()) return Collections.emptySet();

        // 1. Intersect candidates containing all words
        Set<Integer> candidates = new HashSet<>(evaluateTerm(words.get(0)));
        for (int i = 1; i < words.size(); i++) {
            candidates.retainAll(evaluateTerm(words.get(i)));
            if (candidates.isEmpty()) return Collections.emptySet();
        }

        // 2. Pre-index positions per candidate in O(1) maps
        List<Map<Integer, List<Integer>>> wordDocPositions = new ArrayList<>(words.size());
        for (String w : words) {
            Map<Integer, List<Integer>> map = new HashMap<>(candidates.size() * 4 / 3 + 1);
            for (Posting p : index.getPostings(w)) {
                if (candidates.contains(p.docId())) {
                    map.put(p.docId(), p.positions());
                }
            }
            wordDocPositions.add(map);
        }

        // 3. Verify positional adjacency (p, p+1, p+2...)
        Set<Integer> result = new HashSet<>();
        for (int docId : candidates) {
            List<Integer> firstPositions = wordDocPositions.get(0).get(docId);
            if (firstPositions == null || firstPositions.isEmpty()) continue;

            for (int start : firstPositions) {
                boolean match = true;
                for (int i = 1; i < words.size(); i++) {
                    List<Integer> nextPositions = wordDocPositions.get(i).get(docId);
                    if (nextPositions == null || !nextPositions.contains(start + i)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    result.add(docId);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * High-performance BM25 ranking in O(postings) time instead of O(docIds * postings):
     * Iterates over query terms and their postings directly, accumulating scores into matching docIds.
     */
    private List<SearchResult> rank(Set<Integer> docIds, QueryNode query) {
        List<String> terms = new ArrayList<>();
        collectTerms(query, terms, true);
        terms.sort(String::compareTo);
        Map<Integer, Double> scores = new HashMap<>(docIds.size() * 4 / 3 + 1);
        double avgdl = Math.max(1.0, index.getAvgDocLength());
        int N = Math.max(1, index.getDocumentCount());

        for (String term : terms) {
            List<Posting> postings = index.getPostings(term);
            int n = postings.size();
            if (n == 0) continue;

            double idf = Math.log((N - n + 0.5) / (n + 0.5) + 1.0);

            for (Posting p : postings) {
                int docId = p.docId();
                if (!docIds.contains(docId)) continue; // O(1) set lookup

                int f = p.positions().size();
                Document doc = index.getDocument(docId);
                int docLen = (doc != null) ? doc.length() : (int) avgdl;

                double numerator = f * (k1 + 1);
                double denominator = f + k1 * (1.0 - b + b * (docLen / avgdl));
                double termScore = idf * (numerator / denominator);

                scores.merge(docId, termScore, Double::sum);
            }
        }

        Comparator<SearchResult> best = Comparator.comparingDouble(SearchResult::score).reversed()
                .thenComparingInt(SearchResult::docId);
        PriorityQueue<SearchResult> heap = new PriorityQueue<>(topK, best.reversed());
        for (int id : docIds) {
            SearchResult hit = new SearchResult(id, index.getDocument(id).title(), scores.getOrDefault(id, 0.0));
            if (heap.size() == topK) {
                if (best.compare(hit, heap.peek()) >= 0) continue;
                heap.remove();
            }
            heap.add(hit);
        }
        List<SearchResult> result = new ArrayList<>(heap);
        result.sort(best);
        return result;
    }

    private void collectTerms(QueryNode node, List<String> terms, boolean positive) {
        switch (node) {
            case TermNode n -> { if (positive) terms.add(n.word()); }
            case PhraseNode n -> { if (positive) terms.addAll(n.words()); }
            case AndNode n -> {
                collectTerms(n.left(), terms, positive);
                collectTerms(n.right(), terms, positive);
            }
            case OrNode n -> {
                collectTerms(n.left(), terms, positive);
                collectTerms(n.right(), terms, positive);
            }
            case NotNode n -> collectTerms(n.child(), terms, !positive);
        }
    }
}
