package io.conflictradar.processing.service.events;

import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"article-processed-test", "entity-extracted-test", "location-detected-test", "sentiment-analyzed-test"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@ActiveProfiles("test")
class ProcessingEventPublisherIntegrationTest {

    @Autowired
    private ProcessingEventPublisher eventPublisher;

    @Test
    @DisplayName("Should publish article processed event to real Kafka")
    void shouldPublishArticleProcessedEventToRealKafka() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "kafka-integration-123", "Test article", "https://test.com", "Source",
                LocalDateTime.now(), 0.6, Set.of("test"), LocalDateTime.now()
        );

        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<Void> result = eventPublisher.publishArticleProcessed(
                event, entityResult, 0.75, "London", "51.5,-0.1",
                List.of("London"), 0.0, Set.of("news")
        );

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should publish all events successfully")
    void shouldPublishAllEventsSuccessfully() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "all-events-456", "Complex article", "https://test.com", "Source",
                LocalDateTime.now(), 0.8, Set.of("complex"), LocalDateTime.now()
        );

        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<Void> result = eventPublisher.publishAllEvents(
                event, entityResult, 0.85, "Paris", "48.8,2.3",
                List.of("Paris", "France"), -0.1, Set.of("geopolitical")
        );

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should handle entity extracted event publishing")
    void shouldHandleEntityExtractedEventPublishing() {
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<Void> result = eventPublisher.publishEntityExtracted("entity-test-789", entityResult);

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should handle location detected event publishing")
    void shouldHandleLocationDetectedEventPublishing() {
        CompletableFuture<Void> result = eventPublisher.publishLocationDetected(
                "location-test-101", "Berlin", "52.5,13.4", List.of("Berlin", "Germany"), 0.9
        );

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should handle sentiment analyzed event publishing")
    void shouldHandleSentimentAnalyzedEventPublishing() {
        CompletableFuture<Void> result = eventPublisher.publishSentimentAnalyzed(
                "sentiment-test-202", -0.3, -0.5, 0.1, -0.2, "rule-based", 0.8
        );

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
    }
}
