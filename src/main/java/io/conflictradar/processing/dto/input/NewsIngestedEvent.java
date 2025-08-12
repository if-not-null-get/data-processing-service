package io.conflictradar.processing.dto.input;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Set;

public record NewsIngestedEvent(
        @JsonProperty("articleId") String articleId,
        @JsonProperty("title") String title,
        @JsonProperty("link") String link,
        @JsonProperty("source") String source,
        @JsonProperty("publishedAt")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime publishedAt,
        @JsonProperty("riskScore") double riskScore,
        @JsonProperty("conflictKeywords") Set<String> conflictKeywords,
        @JsonProperty("processedAt")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime processedAt
) {

    /**
     * Check if this is a high-risk article requiring immediate processing
     */
    public boolean isHighRisk() {
        return riskScore > 0.7;
    }

    /**
     * Check if article contains critical keywords
     */
    public boolean isCritical() {
        return conflictKeywords.stream()
                .anyMatch(keyword -> Set.of("nuclear", "terrorism", "genocide").contains(keyword.toLowerCase()));
    }

    /**
     * Get simplified source name
     */
    public String getSimpleSource() {
        return switch (source.toLowerCase()) {
            case "bbc" -> "BBC";
            case "reuters" -> "Reuters";
            case "cnn" -> "CNN";
            default -> source;
        };
    }
}
