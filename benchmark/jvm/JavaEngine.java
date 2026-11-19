import com.minigoogle.index.InvertedIndex;
import com.minigoogle.model.Document;
import com.minigoogle.search.SearchEngine;
import java.util.List;

public final class JavaEngine extends Suite.Engine {
    private final InvertedIndex index = new InvertedIndex();
    private final SearchEngine engine = new SearchEngine(index);
    void build(List<Suite.Row> rows) {
        rows.forEach(this::insert);
        index.vocabulary().forEach(trie::add);
    }
    void insert(Suite.Row r) { index.addDocument(new Document(r.id(), r.title(), r.body(), r.length())); }
    int frequency(String term) { return index.getPostings(term).size(); }
    int size() { return index.getDocumentCount(); }
    List<Suite.Hit> search(String q) {
        return engine.search(q).stream().map(r -> new Suite.Hit(r.docId(), r.score())).toList();
    }
}
