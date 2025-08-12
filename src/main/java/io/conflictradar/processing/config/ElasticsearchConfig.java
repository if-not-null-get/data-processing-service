package io.conflictradar.processing.config;

import java.time.Duration;

public record ElasticsearchConfig(
        String clusterName,
        Indices indices,
        Indexing indexing
) {
    public record Indices(
            String articles,
            String entities,
            String locations,
            String analytics
    ) {}

    public record Indexing(
            int batchSize,
            Duration flushInterval,
            int maxRetries,
            boolean enableRefresh
    ) {}
}
