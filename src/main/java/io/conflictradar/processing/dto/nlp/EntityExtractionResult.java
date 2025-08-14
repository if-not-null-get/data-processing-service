package io.conflictradar.processing.dto.nlp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Result of entity extraction from text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntityExtractionResult(
        @JsonProperty("entities") List<ExtractedEntity> entities,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("overallConfidence") double overallConfidence
) {

    /**
     * Create empty result for error cases
     */
    public static EntityExtractionResult empty() {
        return new EntityExtractionResult(Collections.emptyList(), 0, 0.0);
    }

    /**
     * Get entities grouped by type
     */
    @JsonIgnore
    public Map<ExtractedEntity.EntityType, List<ExtractedEntity>> getEntitiesByType() {
        return entities.stream()
                .collect(Collectors.groupingBy(ExtractedEntity::type));
    }

    /**
     * Get only conflict-relevant entities
     */
    @JsonIgnore
    public List<ExtractedEntity> getConflictRelevantEntities() {
        return entities.stream()
                .filter(ExtractedEntity::isConflictRelevant)
                .sorted((a, b) -> Integer.compare(b.getConflictPriority(), a.getConflictPriority()))
                .collect(Collectors.toList());
    }

    /**
     * Get persons (political leaders, military commanders)
     */
    @JsonIgnore
    public List<ExtractedEntity> getPersons() {
        return entities.stream()
                .filter(entity -> entity.type() == ExtractedEntity.EntityType.PERSON)
                .collect(Collectors.toList());
    }

    /**
     * Get organizations (governments, military, NGOs)
     */
    @JsonIgnore
    public List<ExtractedEntity> getOrganizations() {
        return entities.stream()
                .filter(entity -> entity.type() == ExtractedEntity.EntityType.ORGANIZATION)
                .collect(Collectors.toList());
    }

    /**
     * Get locations (countries, cities, regions)
     */
    @JsonIgnore
    public List<ExtractedEntity> getLocations() {
        return entities.stream()
                .filter(entity -> entity.type() == ExtractedEntity.EntityType.LOCATION)
                .collect(Collectors.toList());
    }

    /**
     * Check if extraction found high-priority conflict entities
     */
    @JsonIgnore
    public boolean hasHighPriorityConflictEntities() {
        return entities.stream()
                .anyMatch(entity -> entity.isConflictRelevant() && entity.getConflictPriority() >= 2);
    }

    /**
     * Calculate conflict relevance score
     */
    public double getConflictRelevanceScore() {
        if (entities.isEmpty()) {
            return 0.0;
        }

        double conflictEntityRatio = (double) getConflictRelevantEntities().size() / entities.size();
        double priorityBonus = entities.stream()
                .mapToInt(ExtractedEntity::getConflictPriority)
                .average()
                .orElse(0.0) * 0.1;

        return Math.min(conflictEntityRatio + priorityBonus, 1.0);
    }

    /**
     * Get summary statistics
     */
    public EntityExtractionSummary getSummary() {
        Map<ExtractedEntity.EntityType, List<ExtractedEntity>> byType = getEntitiesByType();

        return new EntityExtractionSummary(
                entities.size(),
                byType.getOrDefault(ExtractedEntity.EntityType.PERSON, Collections.emptyList()).size(),
                byType.getOrDefault(ExtractedEntity.EntityType.ORGANIZATION, Collections.emptyList()).size(),
                byType.getOrDefault(ExtractedEntity.EntityType.LOCATION, Collections.emptyList()).size(),
                getConflictRelevantEntities().size(),
                getConflictRelevanceScore(),
                overallConfidence,
                processingTimeMs
        );
    }

    /**
     * Summary statistics for entity extraction
     */
    public record EntityExtractionSummary(
            @JsonProperty("totalEntities") int totalEntities,
            @JsonProperty("persons") int persons,
            @JsonProperty("organizations") int organizations,
            @JsonProperty("locations") int locations,
            @JsonProperty("conflictRelevant") int conflictRelevant,
            @JsonProperty("conflictRelevanceScore") double conflictRelevanceScore,
            @JsonProperty("overallConfidence") double overallConfidence,
            @JsonProperty("processingTimeMs") long processingTimeMs
    ) {}
}