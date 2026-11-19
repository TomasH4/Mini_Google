package com.minigoogle.loader;

import com.minigoogle.model.Document;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Functional TSV (Tab-Separated Values) document loader.
 * Format: ID \t Title \t Body
 */
public class TsvLoader {

    /** Predicate to filter out invalid or malformed TSV lines */
    private static final Predicate<String[]> VALID_TSV_ROW = parts ->
            parts.length >= 3 && !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank();

    /**
     * Loads all documents from a TSV file into memory as an immutable List.
     */
    public static List<Document> load(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines
                    .map(line -> line.split("\t"))
                    .filter(VALID_TSV_ROW)
                    .map(TsvLoader::parseRow)
                    .toList();
        }
    }

    /**
     * Streams and processes documents line-by-line using a consumer function (memory-efficient).
     *
     * @param path TSV file path
     * @param consumer Higher-order consumer function applied to each loaded document
     * @return Total count of processed documents
     */
    public static int processStream(Path path, Consumer<Document> consumer) throws IOException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (VALID_TSV_ROW.test(parts)) {
                    consumer.accept(parseRow(parts));
                    count++;
                }
            }
        }
        return count;
    }

    private static Document parseRow(String[] parts) {
        int id = Integer.parseInt(parts[0].trim());
        String title = parts[1].trim();
        String body = parts[2].trim();
        int length = body.isEmpty() ? 0 : body.split("\\s+").length;
        return new Document(id, title, body, length);
    }
}
