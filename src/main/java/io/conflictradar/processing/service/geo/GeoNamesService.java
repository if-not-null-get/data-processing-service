package io.conflictradar.processing.service.geo;

import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GeoNamesService {

    private static final Logger logger = LoggerFactory.getLogger(GeoNamesService.class);

    private final WebClient webClient;
    private final ProcessingConfig config;

    public GeoNamesService(ProcessingConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.nlp().geographic().geonamesBaseUrl())
                .build();
    }

    /**
     * Resolve location entities to coordinates with caching
     */
    public GeographicResolutionResult resolveLocations(List<ExtractedEntity> locationEntities) {
        if (locationEntities.isEmpty()) {
            return GeographicResolutionResult.empty();
        }

        logger.debug("Resolving {} location entities to coordinates", locationEntities.size());

        try {
            // Find primary location (highest confidence or first conflict zone)
            ExtractedEntity primaryEntity = findPrimaryLocation(locationEntities);

            GeoLocation primaryLocation = null;
            if (primaryEntity != null) {
                primaryLocation = resolveLocation(primaryEntity.text()).get();
            }

            // Resolve other locations (limit to avoid API rate limits)
            List<GeoLocation> allLocations = locationEntities.stream()
                    .limit(5) // Limit to 5 locations to avoid rate limits
                    .map(entity -> resolveLocation(entity.text()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            double overallConfidence = calculateOverallConfidence(allLocations);

            return new GeographicResolutionResult(
                    primaryLocation,
                    allLocations,
                    overallConfidence,
                    System.currentTimeMillis()
            );

        } catch (Exception e) {
            logger.error("Failed to resolve geographic locations: {}", e.getMessage(), e);
            return GeographicResolutionResult.empty();
        }
    }

    /**
     * Resolve single location to coordinates with caching
     */
    @Cacheable(value = "geoResolution", key = "#locationName.toLowerCase()")
    public Optional<GeoLocation> resolveLocation(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            logger.debug("Resolving location: {}", locationName);

            // Query GeoNames API
            GeoNamesResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/searchJSON")
                            .queryParam("q", locationName.trim())
                            .queryParam("maxRows", "1")
                            .queryParam("featureClass", "P") // Populated places
                            .queryParam("username", config.nlp().geographic().geonamesApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(GeoNamesResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.geonames() != null && !response.geonames().isEmpty()) {
                GeoNamesResponse.GeoName geoName = response.geonames().get(0);

                GeoLocation location = new GeoLocation(
                        geoName.name(),
                        geoName.countryName(),
                        geoName.lat(),
                        geoName.lng(),
                        formatCoordinates(geoName.lat(), geoName.lng()),
                        calculateConfidence(locationName, geoName),
                        isConflictZone(geoName.name(), geoName.countryName())
                );

                logger.debug("Resolved '{}' to: {} ({}, {}) confidence: {:.2f}",
                        locationName, location.name(), location.latitude(), location.longitude(), location.confidence());

                return Optional.of(location);
            } else {
                logger.debug("No results found for location: {}", locationName);
                return Optional.empty();
            }

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                logger.warn("GeoNames API rate limit exceeded for: {}", locationName);
            } else {
                logger.error("GeoNames API error for '{}': {} {}",
                        locationName, e.getStatusCode(), e.getResponseBodyAsString());
            }
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Failed to resolve location '{}': {}", locationName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Find the most important location (conflict zones get priority)
     */
    private ExtractedEntity findPrimaryLocation(List<ExtractedEntity> locations) {
        // Priority 1: Conflict zones
        Optional<ExtractedEntity> conflictZone = locations.stream()
                .filter(ExtractedEntity::isConflictRelevant)
                .findFirst();

        return conflictZone.orElseGet(() -> locations.stream()
                .max(Comparator.comparingDouble(ExtractedEntity::confidence))
                .orElse(null));

        // Priority 2: Highest confidence
    }

    /**
     * Calculate confidence based on name matching and other factors
     */
    private double calculateConfidence(String searchTerm, GeoNamesResponse.GeoName geoName) {
        String searchLower = searchTerm.toLowerCase().trim();
        String resultLower = geoName.name().toLowerCase();

        // Exact match
        if (searchLower.equals(resultLower)) {
            return 0.95;
        }

        // Contains match
        if (resultLower.contains(searchLower) || searchLower.contains(resultLower)) {
            return 0.8;
        }

        // Population boost (larger cities are more likely to be correct)
        double populationBoost = Math.min(geoName.population() / 1_000_000.0 * 0.1, 0.1);

        return Math.min(0.7 + populationBoost, 0.9);
    }

    /**
     * Check if location is in a known conflict zone
     */
    private boolean isConflictZone(String cityName, String countryName) {
        String[] conflictCountries = {
                "ukraine", "syria", "afghanistan", "iraq", "yemen", "somalia", "sudan", "myanmar"
        };

        String[] conflictCities = {
                "gaza", "donetsk", "mariupol", "kharkiv", "aleppo", "kabul", "baghdad"
        };

        String cityLower = cityName.toLowerCase();
        String countryLower = countryName.toLowerCase();

        for (String country : conflictCountries) {
            if (countryLower.contains(country)) {
                return true;
            }
        }

        for (String city : conflictCities) {
            if (cityLower.contains(city)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Format coordinates as "latitude,longitude" string
     */
    private String formatCoordinates(double lat, double lng) {
        return String.format("%.6f,%.6f", lat, lng);
    }

    /**
     * Calculate overall confidence for geographic resolution
     */
    private double calculateOverallConfidence(List<GeoLocation> locations) {
        if (locations.isEmpty()) {
            return 0.0;
        }

        double averageConfidence = locations.stream()
                .mapToDouble(GeoLocation::confidence)
                .average()
                .orElse(0.0);

        // Boost if we found conflict zones
        boolean hasConflictZones = locations.stream().anyMatch(GeoLocation::isConflictZone);
        if (hasConflictZones) {
            averageConfidence = Math.min(averageConfidence + 0.1, 1.0);
        }

        return averageConfidence;
    }

    /**
     * Health check for GeoNames API
     */
    public boolean isHealthy() {
        try {
            // Simple test query
            Optional<GeoLocation> testResult = resolveLocation("London");
            return testResult.isPresent();
        } catch (Exception e) {
            logger.warn("GeoNames health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * GeoNames API response model
     */
    public record GeoNamesResponse(
            List<GeoName> geonames
    ) {
        public record GeoName(
                String name,
                String countryName,
                double lat,
                double lng,
                int population
        ) {}
    }

    /**
     * Resolved geographic location
     */
    public record GeoLocation(
            String name,
            String country,
            double latitude,
            double longitude,
            String coordinates,
            double confidence,
            boolean isConflictZone
    ) {}

    /**
     * Result of geographic resolution
     */
    public record GeographicResolutionResult(
            GeoLocation primaryLocation,
            List<GeoLocation> allLocations,
            double overallConfidence,
            long processingTimeMs
    ) {
        public static GeographicResolutionResult empty() {
            return new GeographicResolutionResult(null, List.of(), 0.0, 0);
        }

        public boolean hasResults() {
            return primaryLocation != null || !allLocations.isEmpty();
        }

        public List<String> getLocationNames() {
            return allLocations.stream()
                    .map(GeoLocation::name)
                    .toList();
        }

        public String getPrimaryCoordinates() {
            return primaryLocation != null ? primaryLocation.coordinates() : null;
        }

        public List<GeoLocation> getConflictZones() {
            return allLocations.stream()
                    .filter(GeoLocation::isConflictZone)
                    .toList();
        }
    }
}