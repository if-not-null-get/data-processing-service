package io.conflictradar.processing.dto.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SentimentAnalyzedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("articleId") String articleId,
        @JsonProperty("overallSentiment") double overallSentiment,
        @JsonProperty("aspects") SentimentAspects aspects,
        @JsonProperty("approach") String approach,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("analyzedAt") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime analyzedAt
) {
    public record SentimentAspects(
            @JsonProperty("violence") double violence,
            @JsonProperty("diplomacy") double diplomacy,
            @JsonProperty("economy") double economy,
            @JsonProperty("humanitarian") double humanitarian
    ) {}

    public static SentimentAnalyzedEvent create(String articleId, double overallSentiment,
                                                SentimentAspects aspects, String approach, double confidence) {
        return new SentimentAnalyzedEvent(
                "sentiment-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                articleId, overallSentiment, aspects, approach, confidence, LocalDateTime.now()
        );
    }
}
