import java.util.*;
import java.util.function.ToIntFunction;

/** Trie used by the two JVM adapters. Frequencies come from the backend's index. */
final class Vocabulary {
    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        String term;
    }
    private final Node root = new Node();
    void add(String term) {
        Node n = root;
        for (char c : term.toCharArray()) n = n.children.computeIfAbsent(c, k -> new Node());
        n.term = term;
    }
    String resolve(String text, boolean fuzzy, ToIntFunction<String> frequency) {
        var candidates = new ArrayList<String>();
        if (fuzzy) {
            int[] row = new int[text.length() + 1];
            for (int i = 0; i < row.length; i++) row[i] = i;
            for (int edits = 0; edits <= 2 && candidates.isEmpty(); edits++)
                collect(root, text, row, edits, candidates);
        } else {
            Node node = root;
            for (char c : text.toCharArray()) {
                node = node.children.get(c);
                if (node == null) return "";
            }
            complete(node, candidates);
        }
        return candidates.stream().min(Comparator.comparingInt(frequency).reversed()
                .thenComparing(Comparator.naturalOrder())).orElse("");
    }
    private void complete(Node node, List<String> matches) {
        if (node.term != null) matches.add(node.term);
        node.children.values().forEach(child -> complete(child, matches));
    }
    private void collect(Node node, String text, int[] row, int edits, List<String> matches) {
        if (node.term != null && row[text.length()] <= edits) matches.add(node.term);
        for (var edge : node.children.entrySet()) {
            int[] next = new int[row.length];
            int low = next[0] = row[0] + 1;
            for (int i = 1; i < row.length; i++) {
                int substitution = row[i-1] + (edge.getKey() == text.charAt(i-1) ? 0 : 1);
                next[i] = Math.min(substitution, Math.min(next[i-1], row[i]) + 1);
                low = Math.min(low, next[i]);
            }
            if (low <= edits) collect(edge.getValue(), text, next, edits, matches);
        }
    }
}
