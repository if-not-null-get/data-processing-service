package io.conflictradar.processing.dto.nlp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * Represents a named entity extracted from text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedEntity(
        @JsonProperty("text") String text,
        @JsonProperty("type") EntityType type,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("startPosition") int startPosition,
        @JsonProperty("endPosition") int endPosition
) {

    public enum EntityType {
        @JsonProperty("PERSON")
        PERSON("PERSON", "Political leaders, military commanders, journalists"),

        @JsonProperty("ORGANIZATION")
        ORGANIZATION("ORGANIZATION", "Governments, military units, terrorist groups, NGOs"),

        @JsonProperty("LOCATION")
        LOCATION("LOCATION", "Countries, cities, regions, geographic areas"),

        @JsonProperty("OTHER")
        OTHER("OTHER", "Other named entities");

        private final String code;
        private final String description;

        EntityType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Check if this entity is likely to be conflict-related
     */
    public boolean isConflictRelevant() {
        String lowerText = text.toLowerCase();

        // Military/political organizations
        if (type == EntityType.ORGANIZATION) {
            return lowerText.contains("military") ||
                    lowerText.contains("army") ||
                    lowerText.contains("nato") ||
                    lowerText.contains("un") ||
                    lowerText.contains("security council") ||
                    lowerText.contains("pentagon") ||
                    lowerText.contains("ministry of defense");
        }

        // Political leaders
        if (type == EntityType.PERSON) {
            return lowerText.contains("president") ||
                    lowerText.contains("minister") ||
                    lowerText.contains("general") ||
                    lowerText.contains("commander");
        }

        // Conflict zones
        if (type == EntityType.LOCATION) {
            return isConflictZone(lowerText);
        }

        return false;
    }

    private boolean isConflictZone(String location) {
        // Known conflict/tension areas (this could be externalized to config)
        return Set.of(
                "ukraine", "russia", "syria", "afghanistan", "iraq",
                "gaza", "israel", "palestine", "kashmir", "taiwan",
                "south china sea", "crimea", "donetsk", "donbass"
        ).stream().anyMatch(location::contains);
    }

    /**
     * Get entity priority for conflict analysis
     */
    public int getConflictPriority() {
        if (!isConflictRelevant()) {
            return 0;
        }

        return switch (type) {
            case PERSON -> 3;        // High priority - key actors
            case ORGANIZATION -> 2;  // Medium priority - institutions
            case LOCATION -> 1;      // Lower priority - geography
            case OTHER -> 0;
        };
    }

    private static final Set<String> CONFLICT_ZONES = Set.of(
            "ukraine", "russia", "syria", "afghanistan", "iraq",
            "gaza", "israel", "palestine", "kashmir", "taiwan",
            "south china sea", "crimea", "donetsk", "donbass",
            "lebanon", "yemen", "somalia", "sudan", "myanmar"
    );
}