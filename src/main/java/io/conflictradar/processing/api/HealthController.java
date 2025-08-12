package io.conflictradar.processing.api;

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

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var healthInfo = Map.of(
                "status", "UP",
                "service", serviceName,
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0",
                "processing", Map.of(
                        "kafkaConsumerActive", true,
                        "elasticsearchConnected", true, // TODO: real health check
                        "nlpModelsLoaded", false, // TODO: real check
                        "geonamesApiReady", false // TODO: real check
                )
        );

        return ResponseEntity.ok(healthInfo);
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
                        "nlpModels", "Loading..."
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
