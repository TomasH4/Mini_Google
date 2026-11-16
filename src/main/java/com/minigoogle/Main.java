package com.minigoogle;

import com.minigoogle.index.InvertedIndex;
import com.minigoogle.model.Document;
import com.minigoogle.search.SearchEngine;
import com.minigoogle.model.SearchResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple console application that loads example documents from the classpath,
 * builds the inverted index and allows the user to run queries.
 */
public class Main {
    private static final String DOCS_PATH = "/documents"; // resources folder

    public static void main(String[] args) throws IOException {
        InvertedIndex index = new InvertedIndex();
        loadDocuments(index);
        SearchEngine engine = new SearchEngine(index);

        System.out.println("Mini‑Google (Java) ready. Type a query and press ENTER. Empty line to exit.");
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("query> ");
                String line = scanner.nextLine();
                if (line == null || line.trim().isEmpty()) break;
                List<SearchResult> results = engine.search(line);
                if (results.isEmpty()) {
                    System.out.println("No results.");
                } else {
                    System.out.println("Top results:");
                    for (SearchResult r : results) {
                        System.out.printf("- %s (score %.4f)\n", r.title(), r.score());
                    }
                }
            }
        }
        System.out.println("Goodbye!");
    }

    private static void loadDocuments(InvertedIndex index) throws IOException {
        // List files under /documents in the classpath
        InputStream manifest = Main.class.getResourceAsStream(DOCS_PATH + "/manifest.txt");
        if (manifest == null) {
            System.err.println("No manifest found for documents.");
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
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String title = name.replaceAll("\\.txt$", "");
                int length = content.split("\\s+").length;
                Document doc = new Document(id++, title, content, length);
                index.addDocument(doc);
            }
        }
        System.out.println("Loaded " + (id - 1) + " documents into the index.");
    }

    // Helper to load a document of any supported type (txt, md, pdf, docx)
    private static void loadDocument(InvertedIndex index, String name, int id) throws IOException {
        try (InputStream is = Main.class.getResourceAsStream(DOCS_PATH + "/" + name)) {
            if (is == null) return;
            String content = com.minigoogle.loader.DocumentLoader.load(is, name);
            String title = name.replaceAll("\\.[^.]+$", "");
            int length = content.split("\\s+").length;
            Document doc = new Document(id, title, content, length);
            index.addDocument(doc);
        }
    }
}
