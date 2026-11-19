package com.minigoogle;

import com.minigoogle.benchmark.BenchmarkRunner;
import com.minigoogle.index.InvertedIndex;
import com.minigoogle.loader.TsvLoader;
import com.minigoogle.model.Document;
import com.minigoogle.model.SearchResult;
import com.minigoogle.search.SearchEngine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main entry point for Mini-Google.
 * Supports interactive console search, TSV loading and automated benchmarking.
 */
public class Main {
    private static final String DOCS_PATH = "/documents"; // resources folder

    public static void main(String[] args) throws Exception {
        // 1. Check if user wants to run the automated benchmark
        if (args.length > 0 && args[0].equalsIgnoreCase("--benchmark")) {
            Path corpusPath = args.length > 1 ? Path.of(args[1]) : Path.of("corpus.tsv");
            Path queriesPath = args.length > 2 ? Path.of(args[2]) : Path.of("queries.txt");
            Path outputPath = args.length > 3 ? Path.of(args[3]) : Path.of("benchmark_results_java.json");

            if (!Files.exists(corpusPath)) {
                System.err.printf("Error: No se encontró el archivo de corpus en '%s'.\n", corpusPath.toAbsolutePath());
                System.err.println("Ejecuta primero: python generate_dataset.py");
                System.exit(1);
            }
            if (!Files.exists(queriesPath)) {
                System.err.printf("Error: No se encontró el archivo de consultas en '%s'.\n", queriesPath.toAbsolutePath());
                System.err.println("Ejecuta primero: python generate_dataset.py");
                System.exit(1);
            }

            BenchmarkRunner.run(corpusPath, queriesPath, outputPath);
            return;
        }

        // 2. Interactive Console Mode
        InvertedIndex index = new InvertedIndex();

        // If a TSV is passed or exists in local folder, load it; otherwise fallback to classpath resources
        Path localTsv = args.length > 1 && args[0].equalsIgnoreCase("--tsv") ? Path.of(args[1]) : Path.of("corpus.tsv");
        if (Files.exists(localTsv)) {
            System.out.printf("Cargando corpus masivo desde '%s'...\n", localTsv);
            int count = TsvLoader.processStream(localTsv, index::addDocument);
            System.out.printf("Cargados %d documentos en el índice invertido.\n", count);
        } else {
            loadClasspathDocuments(index);
        }

        SearchEngine engine = new SearchEngine(index);

        System.out.println("=================================================================");
        System.out.println(" Mini-Google (Java 21) listo. Escribe tu consulta booleana o frase.");
        System.out.println(" Ejemplos:  java OR scala  |  (backend OR hilos) AND NOT lento  |  \"rapido y seguro\"");
        System.out.println(" Presiona ENTER sin texto para salir.");
        System.out.println("=================================================================");

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nquery> ");
                String line = scanner.nextLine();
                if (line == null || line.trim().isEmpty()) break;

                long start = System.nanoTime();
                List<SearchResult> results = engine.search(line);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                if (results.isEmpty()) {
                    System.out.printf("No se encontraron resultados (%d ms).\n", elapsedMs);
                } else {
                    System.out.printf("Top resultados (%d ms):\n", elapsedMs);
                    for (SearchResult r : results) {
                        System.out.printf("  [%d] %s (score BM25: %.4f)\n", r.docId(), r.title(), r.score());
                    }
                }
            }
        }
        System.out.println("¡Hasta luego!");
    }

    private static void loadClasspathDocuments(InvertedIndex index) throws IOException {
        InputStream manifest = Main.class.getResourceAsStream(DOCS_PATH + "/manifest.txt");
        if (manifest == null) {
            System.err.println("No se encontró manifest.txt para documentos.");
            return;
        }
        List<String> fileNames = new BufferedReader(new InputStreamReader(manifest, StandardCharsets.UTF_8))
                .lines()
                .filter(l -> !l.isBlank())
                .collect(Collectors.toList());

        int id = 1;
        for (String name : fileNames) {
            try (InputStream is = Main.class.getResourceAsStream(DOCS_PATH + "/" + name)) {
                if (is == null) continue;
                String content = com.minigoogle.loader.DocumentLoader.load(is, name);
                String title = name.replaceAll("\\.[^.]+$", "");
                int length = content.split("\\s+").length;
                Document doc = new Document(id++, title, content, length);
                index.addDocument(doc);
            }
        }
        System.out.println("Cargados " + (id - 1) + " documentos de ejemplo en el índice.");
    }
}
