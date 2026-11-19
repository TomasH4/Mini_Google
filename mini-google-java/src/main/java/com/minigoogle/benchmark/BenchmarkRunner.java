package com.minigoogle.benchmark;

import com.minigoogle.index.InvertedIndex;
import com.minigoogle.loader.TsvLoader;
import com.minigoogle.model.Document;
import com.minigoogle.search.SearchEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Benchmark runner that measures execution time, memory footprint,
 * query throughput and concurrency using Java 21 Virtual Threads.
 */
public class BenchmarkRunner {

    public static BenchmarkResult run(Path corpusPath, Path queriesPath, Path outputPath) throws Exception {
        System.out.println("=================================================================");
        System.out.println("           MINI-GOOGLE BENCHMARK SUITE (JAVA 21)                 ");
        System.out.println("=================================================================");

        // 1. Memory before indexing
        triggerGc();
        long memBefore = getUsedMemoryBytes();

        // 2. Indexing Phase
        System.out.printf("[1/3] Indexando corpus desde '%s'...\n", corpusPath);
        InvertedIndex index = new InvertedIndex();
        long indexStart = System.nanoTime();

        int docCount = TsvLoader.processStream(corpusPath, index::addDocument);

        long indexTimeMs = (System.nanoTime() - indexStart) / 1_000_000;
        triggerGc();
        long memAfter = getUsedMemoryBytes();
        double memoryUsedMb = Math.max(0.0, (memAfter - memBefore) / (1024.0 * 1024.0));

        System.out.printf("      -> Documentos indexados: %d\n", docCount);
        System.out.printf("      -> Términos en vocabulario: %d\n", index.getVocabularySize());
        System.out.printf("      -> Tiempo de indexación: %d ms (%.2f s)\n", indexTimeMs, indexTimeMs / 1000.0);
        System.out.printf("      -> Huella de memoria RAM estimada: %.2f MB\n", memoryUsedMb);

        // 3. Load Queries
        System.out.printf("\n[2/3] Cargando consultas desde '%s'...\n", queriesPath);
        List<String> queries;
        try (Stream<String> lines = Files.lines(queriesPath, StandardCharsets.UTF_8)) {
            queries = lines.map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        System.out.printf("      -> Consultas cargadas: %d\n", queries.size());

        SearchEngine engine = new SearchEngine(index);

        // Warmup (50 queries to trigger JIT optimization)
        int warmupCount = Math.min(50, queries.size());
        for (int i = 0; i < warmupCount; i++) {
            engine.search(queries.get(i));
        }

        // 4. Sequential Query Benchmark (Latency & Baseline Throughput)
        System.out.println("\n[3/3] Ejecutando consultas...");
        System.out.println("      (a) Modo Secuencial (medición de latencias individuales)...");

        List<Double> latenciesMs = new ArrayList<>(queries.size());
        long seqStart = System.nanoTime();

        for (String q : queries) {
            long qStart = System.nanoTime();
            engine.search(q);
            long qElapsed = System.nanoTime() - qStart;
            latenciesMs.add(qElapsed / 1_000_000.0);
        }

        long seqTotalNs = System.nanoTime() - seqStart;
        long seqTimeMs = seqTotalNs / 1_000_000;
        double seqThroughput = queries.size() / (seqTotalNs / 1_000_000_000.0);

        latenciesMs.sort(Double::compareTo);
        double meanLatency = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p50 = percentile(latenciesMs, 50.0);
        double p95 = percentile(latenciesMs, 95.0);
        double p99 = percentile(latenciesMs, 99.0);

        System.out.printf("          -> Tiempo total secuencial: %d ms\n", seqTimeMs);
        System.out.printf("          -> Throughput secuencial: %.2f queries/seg\n", seqThroughput);
        System.out.printf("          -> Latencia Media: %.4f ms\n", meanLatency);
        System.out.printf("          -> Latencia p50: %.4f ms | p95: %.4f ms | p99: %.4f ms\n", p50, p95, p99);

        // 5. Concurrent Query Benchmark (Java 21 Virtual Threads)
        int virtualThreads = queries.size();
        System.out.printf("      (b) Modo Concurrente con Virtual Threads (%d hilos concurrentes)...\n", virtualThreads);

        long concurrentStart = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = queries.stream()
                    .map(q -> executor.submit(() -> engine.search(q)))
                    .toList();
            for (var f : futures) {
                f.get();
            }
        }
        long concurrentTotalNs = System.nanoTime() - concurrentStart;
        long concurrentTimeMs = concurrentTotalNs / 1_000_000;
        double concurrentThroughput = queries.size() / (concurrentTotalNs / 1_000_000_000.0);

        System.out.printf("          -> Tiempo total concurrente: %d ms\n", concurrentTimeMs);
        System.out.printf("          -> Throughput concurrente: %.2f queries/seg\n", concurrentThroughput);

        // 6. Build Result
        BenchmarkResult result = new BenchmarkResult(
                "Java 21",
                docCount,
                indexTimeMs,
                memoryUsedMb,
                queries.size(),
                seqTimeMs,
                seqThroughput,
                meanLatency,
                p50,
                p95,
                p99,
                concurrentTimeMs,
                concurrentThroughput,
                virtualThreads
        );

        // 7. Save to JSON
        if (outputPath != null) {
            Files.writeString(outputPath, result.toJson(), StandardCharsets.UTF_8);
            System.out.printf("\n[OK] Métricas consolidadas exportadas exitosamente en:\n     -> %s\n", outputPath.toAbsolutePath());
        }

        System.out.println("=================================================================\n");
        return result;
    }

    private static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0.0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private static long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void triggerGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
    }
}
