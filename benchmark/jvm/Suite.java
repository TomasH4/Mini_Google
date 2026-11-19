import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.ref.Reference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.StampedLock;

public final class Suite {
    record Hit(int id, double score) {}
    record Row(int id, String title, String body, int length) {
        static Row parse(String line) {
            String[] fields = line.split("\t", 3);
            return new Row(Integer.parseInt(fields[0]), fields[1], fields[2], fields[2].split("\\s+").length);
        }
    }
    abstract static class Engine {
        final Vocabulary trie = new Vocabulary();
        abstract void build(List<Row> docs);
        abstract void insert(Row row);
        abstract int frequency(String term);
        abstract int size();
        abstract List<Hit> search(String text);
        double mixedLoad(List<String> qs, List<Row> writes) throws Exception {
            return mixed(this, qs, writes);
        }
        void publish(Row row) {
            insert(row);
            for (String term : row.body.split(" ")) trie.add(term);
        }
        List<Hit> lookup(String q) {
            if (q.startsWith("~") || q.endsWith("*")) {
                boolean fuzzy = q.startsWith("~");
                q = trie.resolve(fuzzy ? q.substring(1) : q.substring(0, q.length()-1), fuzzy, this::frequency);
                if (q.isEmpty()) return List.of();
            }
            return search(q);
        }
    }

    private static Engine build(String name, byte[] raw) throws Exception {
        Engine engine = (Engine)Class.forName(name).getConstructor().newInstance();
        engine.build(new String(raw, StandardCharsets.UTF_8).lines().map(Row::parse).toList());
        return engine;
    }
    private static long retained() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
    private static void warm(String name, byte[] raw, List<String> qs, List<Row> writes) throws Exception {
        Engine engine = build(name, raw);
        engine.lookup(qs.getFirst());
        System.out.println("READY");
        for (int i = 0; i < 2000; i++) engine.lookup(qs.get(i % qs.size()));
        engine.mixedLoad(qs, writes.subList(0, 200));
    }
    private static double mixed(Engine engine, List<String> qs, List<Row> writes) throws Exception {
        var lock = new StampedLock();
        var start = new CyclicBarrier(6);
        List<Future<?>> tasks = new ArrayList<>(5);
        int reads = 4 * writes.size();
        int initial = engine.size();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int lane = 0; lane < 5; lane++) {
                final int worker = lane;
                tasks.add(pool.submit(() -> {
                    start.await();
                    if (worker == 4) {
                        for (Row row : writes) {
                            long stamp = lock.writeLock();
                            try {
                                engine.publish(row);
                            } finally { lock.unlockWrite(stamp); }
                        }
                    } else {
                        for (int n = worker; n < reads; n += 4) {
                            long stamp = lock.readLock();
                            try { engine.lookup(qs.get(n)); }
                            finally { lock.unlockRead(stamp); }
                        }
                    }
                    return null;
                }));
            }
            long t = System.nanoTime();
            start.await();
            for (var f : tasks) f.get();
            double seconds = (System.nanoTime()-t)/1e9;
            if (engine.size() != initial + writes.size()) throw new IllegalStateException("missing writes");
            return reads/seconds;
        }
    }
    public static void main(String[] args) throws Exception {
        String name = args[0];
        byte[] raw = Files.readAllBytes(Path.of(args[2]));
        var qs = Files.readAllLines(Path.of(args[3]), StandardCharsets.UTF_8);
        if (args[1].equals("parity")) {
            Engine engine = build(name, raw);
            for (String q : qs) {
                var hits = engine.lookup(q);
                var line = new StringJoiner(",");
                for (Hit hit : hits) line.add(hit.id + ":" + hit.score);
                System.out.println("HITS " + line);
            }
            return;
        }
        var writes = Files.readAllLines(Path.of(args[4])).stream().map(Row::parse).toList();
        warm(name, raw, qs, writes);
        long before = retained();
        long t = System.nanoTime();
        Engine engine = build(name, raw);
        double indexMs = (System.nanoTime()-t)/1e6;
        long heap = retained()-before;
        if (args[1].equals("index")) {
            System.out.println("INDEX " + indexMs + " " + heap/1048576.0);
            Reference.reachabilityFence(raw);
            Reference.reachabilityFence(engine);
            System.in.read();
            return;
        }
        long[] times = new long[qs.size()];
        t = System.nanoTime();
        for (int n = 0; n < qs.size(); n++) {
            long queryStart = System.nanoTime();
            engine.lookup(qs.get(n));
            times[n] = System.nanoTime()-queryStart;
        }
        double qps = qs.size()*1e9/(System.nanoTime()-t);
        int initial = engine.size();
        double concurrent = engine.mixedLoad(qs, writes);
        if (engine.size() != initial + writes.size()) throw new IllegalStateException("missing writes");
        Arrays.sort(times);
        var line = new StringJoiner(" ", "METRICS ", "");
        for (double value : new double[]{indexMs, heap/1048576.0, qps, concurrent}) line.add(Double.toString(value));
        for (double p : new double[]{.5,.9,.95,.99,.999})
            line.add(Double.toString(times[(int)Math.ceil(p*times.length)-1]/1000.0));
        System.out.println(line);
        Reference.reachabilityFence(raw);
        System.in.read(); // Let the parent read the OS peak before this process exits.
    }
}
