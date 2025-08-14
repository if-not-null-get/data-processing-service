package io.conflictradar.processing.dto.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ArticleProcessedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("articleId") String articleId,
        @JsonProperty("title") String title,
        @JsonProperty("link") String link,
        @JsonProperty("source") String source,
        @JsonProperty("publishedAt") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime publishedAt,

        // Original data
        @JsonProperty("originalRiskScore") double originalRiskScore,
        @JsonProperty("originalKeywords") Set<String> originalKeywords,

        // Enhanced analysis
        @JsonProperty("enhancedRiskScore") double enhancedRiskScore,
        @JsonProperty("conflictRelevanceScore") double conflictRelevanceScore,
        @JsonProperty("totalEntities") int totalEntities,
        @JsonProperty("conflictEntities") int conflictEntities,
        @JsonProperty("highPriority") boolean highPriority,

        // Geographic info
        @JsonProperty("primaryLocation") String primaryLocation,
        @JsonProperty("coordinates") String coordinates,
        @JsonProperty("mentionedLocations") List<String> mentionedLocations,

        // Sentiment
        @JsonProperty("sentimentScore") double sentimentScore,

        // Categories
        @JsonProperty("categories") Set<String> categories,

        @JsonProperty("processedAt") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime processedAt
) {
    public static ArticleProcessedEvent create(String articleId, String title, String link,
                                               String source, LocalDateTime publishedAt,
                                               double originalRiskScore, Set<String> originalKeywords,
                                               double enhancedRiskScore, double conflictRelevanceScore,
                                               int totalEntities, int conflictEntities,
                                               String primaryLocation, String coordinates, List<String> mentionedLocations,
                                               double sentimentScore, Set<String> categories) {
        return new ArticleProcessedEvent(
                "processed-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                articleId, title, link, source, publishedAt,
                originalRiskScore, originalKeywords,
                enhancedRiskScore, conflictRelevanceScore, totalEntities, conflictEntities,
                enhancedRiskScore > 0.7 || conflictRelevanceScore > 0.6,
                primaryLocation, coordinates, mentionedLocations,
                sentimentScore, categories,
                LocalDateTime.now()
        );
    }
}
