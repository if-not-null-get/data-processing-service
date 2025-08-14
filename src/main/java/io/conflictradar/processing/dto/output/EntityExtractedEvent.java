package io.conflictradar.processing.dto.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record EntityExtractedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("articleId") String articleId,
        @JsonProperty("entities") List<ExtractedEntityInfo> entities,
        @JsonProperty("totalEntities") int totalEntities,
        @JsonProperty("conflictRelevant") int conflictRelevant,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("extractedAt") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime extractedAt
) {
    public record ExtractedEntityInfo(
            @JsonProperty("text") String text,
            @JsonProperty("type") String type,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("conflictRelevant") boolean conflictRelevant,
            @JsonProperty("priority") int priority
    ) {}

    public static EntityExtractedEvent create(String articleId, List<ExtractedEntityInfo> entities,
                                              double confidence, long processingTimeMs) {
        int conflictRelevant = (int) entities.stream().filter(ExtractedEntityInfo::conflictRelevant).count();

        return new EntityExtractedEvent(
                "entity-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                articleId, entities, entities.size(), conflictRelevant,
                confidence, processingTimeMs, LocalDateTime.now()
        );
    }
}
