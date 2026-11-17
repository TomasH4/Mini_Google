package com.minigoogle.benchmark;

/**
 * Record holding all experimental performance and resource metrics
 * as specified in the course rubric.
 */
public record BenchmarkResult(
        String language,
        int totalDocuments,
        long indexTimeMs,
        double memoryUsedMb,
        int totalQueries,
        long sequentialTimeMs,
        double sequentialThroughputQps,
        double meanLatencyMs,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        long concurrentTimeMs,
        double concurrentThroughputQps,
        int virtualThreadsCount
) {
    /** Formatted JSON representation for automated cross-language comparison */
    public String toJson() {
        return String.format(java.util.Locale.US,
            """
            {
              "language": "%s",
              "totalDocuments": %d,
              "indexTimeMs": %d,
              "memoryUsedMb": %.2f,
              "totalQueries": %d,
              "sequentialTimeMs": %d,
              "sequentialThroughputQps": %.2f,
              "meanLatencyMs": %.4f,
              "p50LatencyMs": %.4f,
              "p95LatencyMs": %.4f,
              "p99LatencyMs": %.4f,
              "concurrentTimeMs": %d,
              "concurrentThroughputQps": %.2f,
              "virtualThreadsCount": %d
            }
            """,
            language,
            totalDocuments,
            indexTimeMs,
            memoryUsedMb,
            totalQueries,
            sequentialTimeMs,
            sequentialThroughputQps,
            meanLatencyMs,
            p50LatencyMs,
            p95LatencyMs,
            p99LatencyMs,
            concurrentTimeMs,
            concurrentThroughputQps,
            virtualThreadsCount
        );
    }
}
