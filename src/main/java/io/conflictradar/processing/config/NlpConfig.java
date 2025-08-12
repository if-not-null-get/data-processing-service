package io.conflictradar.processing.config;

import java.time.Duration;
import java.util.List;

public record NlpConfig(
        Stanford stanford,
        Geographic geographic,
        Sentiment sentiment
) {
    public record Stanford(
            String modelsPath,
            List<String> annotators,
            int timeout,
            boolean enableCache
    ) {}

    public record Geographic(
            String geonamesApiKey,
            String geonamesBaseUrl,
            Duration cacheTtl,
            int maxRetries
    ) {}

    public record Sentiment(
            String approach, // "rule-based" or "ml"
            double neutralThreshold,
            boolean enableAspectSentiment
    ) {}
}
