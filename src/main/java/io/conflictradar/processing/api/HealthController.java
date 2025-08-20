package io.conflictradar.processing.api;

import io.conflictradar.processing.service.geo.GeoNamesService;
import io.conflictradar.processing.service.nlp.NlpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/processing")
public class HealthController {

    @Value("${spring.application.name}")
    private String serviceName;

    private final NlpService nlpService;
    private final GeoNamesService geoNamesService;

    public HealthController(NlpService nlpService, GeoNamesService geoNamesService) {
        this.nlpService = nlpService;
        this.geoNamesService = geoNamesService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean nlpReady = nlpService.isReady();
        boolean geoReady = geoNamesService.isHealthy();
        boolean overallHealthy = nlpReady && geoReady;

        var healthInfo = Map.of(
                "status", overallHealthy ? "UP" : "DOWN",
                "service", serviceName,
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0",
                "processing", Map.of(
                        "kafkaConsumerActive", true,
                        "elasticsearchConnected", true, // TODO: real health check
                        "nlpModelsLoaded", nlpReady,
                        "geonamesApiReady", geoReady
                )
        );

        return overallHealthy ?
                ResponseEntity.ok(healthInfo) :
                ResponseEntity.status(503).body(healthInfo);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "message", "Data Processing Service is running",
                "capabilities", Map.of(
                        "entityExtraction", "Stanford CoreNLP",
                        "sentimentAnalysis", "Rule-based + ML",
                        "geographicResolution", "GeoNames API",
                        "documentIndexing", "Elasticsearch",
                        "eventStreaming", "Apache Kafka"
                ),
                "infrastructure", Map.of(
                        "elasticsearch", "Connected",
                        "kafka", "Consuming news-ingested",
                        "redis", "Caching entities",
                        "nlpModels", nlpService.isReady() ? "Ready" : "Loading...",
                        "geonames", geoNamesService.isHealthy() ? "Ready" : "Unavailable"
                )
        ));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        // TODO: Real metrics from Micrometer
        return ResponseEntity.ok(Map.of(
                "articlesProcessed", 0,
                "entitiesExtracted", 0,
                "locationsResolved", 0,
                "averageProcessingTime", "0ms",
                "errorRate", "0%"
        ));
    }
}