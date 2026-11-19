import minigoogle.index.InvertedIndex;
import minigoogle.model.Document;
import minigoogle.search.Bm25;
import minigoogle.search.SearchEngine;
import scala.jdk.javaapi.CollectionConverters;
import java.util.List;

public final class ScalaEngine extends Suite.Engine {
    private InvertedIndex index;
    private SearchEngine engine;
    private Document document(Suite.Row r) { return new Document(r.id(), r.title(), r.body(), r.length()); }
    void build(List<Suite.Row> rows) {
        var docs = rows.stream().map(this::document).toList();
        index = InvertedIndex.build(CollectionConverters.asScala(docs)).toOption().get();
        refresh();
        CollectionConverters.asJava(index.postings().keySet()).forEach(trie::add);
    }
    void insert(Suite.Row r) {
        index = index.add(document(r)).toOption().get();
        refresh();
    }
    private void refresh() { engine = new SearchEngine(index, new Bm25(1.5, 0.75), 10); }
    int frequency(String term) { return index.documentFrequency(term); }
    int size() { return index.size(); }
    double mixedLoad(List<String> qs, List<Suite.Row> writes) {
        return minigoogle.bench.MixedFibers.throughput(
                n -> lookup(qs.get(n)), n -> publish(writes.get(n)), writes.size());
    }
    List<Suite.Hit> search(String q) {
        return CollectionConverters.asJava(engine.search(q).toOption().get()).stream()
                .map(r -> new Suite.Hit(r.docId(), r.score())).toList();
    }
}
