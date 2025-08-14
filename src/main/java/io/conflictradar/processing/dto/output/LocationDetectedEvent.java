package io.conflictradar.processing.dto.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record LocationDetectedEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("articleId") String articleId,
        @JsonProperty("primaryLocation") String primaryLocation,
        @JsonProperty("coordinates") String coordinates,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("allLocations") List<String> allLocations,
        @JsonProperty("conflictZones") List<String> conflictZones,
        @JsonProperty("detectedAt") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime detectedAt
) {
    public static LocationDetectedEvent create(String articleId, String primaryLocation,
                                               String coordinates, List<String> allLocations,
                                               double confidence) {
        // Identify conflict zones
        Set<String> knownConflictZones = Set.of("ukraine", "russia", "syria", "afghanistan",
                "gaza", "israel", "palestine", "taiwan", "kashmir");
        List<String> conflictZones = allLocations.stream()
                .filter(loc -> knownConflictZones.stream()
                        .anyMatch(zone -> loc.toLowerCase().contains(zone)))
                .toList();

        return new LocationDetectedEvent(
                "location-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                articleId, primaryLocation, coordinates, confidence,
                allLocations, conflictZones, LocalDateTime.now()
        );
    }
}
