package io.conflictradar.processing.service.events;

import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.output.ArticleProcessedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProcessingEventPublisherTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ProcessingEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ProcessingConfig.KafkaConfig.Topics.Output outputTopics =
                new ProcessingConfig.KafkaConfig.Topics.Output(
                        "article-processed",
                        "entity-extracted",
                        "location-detected",
                        "sentiment-analyzed"
                );

        ProcessingConfig.KafkaConfig.Topics topicsConfig =
                new ProcessingConfig.KafkaConfig.Topics(
                        "news-ingested",
                        "high-risk-detected",
                        outputTopics
                );

        ProcessingConfig.KafkaConfig kafkaConfig =
                new ProcessingConfig.KafkaConfig(
                        "consumer-group-id",
                        "earliest",
                        100,
                        Duration.ofSeconds(10),
                        topicsConfig
                );

        ProcessingConfig config = new ProcessingConfig(
                kafkaConfig,
                null,  // nlp
                null,  // elasticsearch
                null   // performance
        );

        eventPublisher = new ProcessingEventPublisher(kafkaTemplate, config);
    }

    @Test
    @DisplayName("Should publish article processed event successfully")
    void shouldPublishArticleProcessedEventSuccessfully() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<SendResult<String, Object>> mockFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(eq("article-processed"), eq("test-123"), any(ArticleProcessedEvent.class)))
                .thenReturn(mockFuture);

        CompletableFuture<Void> result = eventPublisher.publishArticleProcessed(
                event, entityResult, 0.75, "Ukraine", "50.45,30.52",
                List.of("Ukraine"), -0.2, Set.of("conflict")
        );

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

        verify(kafkaTemplate).send(eq("article-processed"), eq("test-123"), any());
    }

    @Test
    @DisplayName("Should handle kafka send failure")
    void shouldHandleKafkaSendFailure() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        CompletableFuture<Void> result = eventPublisher.publishArticleProcessed(
                event, entityResult, 0.5, null, null, List.of(), 0.0, Set.of()
        );

        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should publish all events concurrently")
    void shouldPublishAllEventsConcurrently() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<SendResult<String, Object>> successFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));


        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(successFuture);

        CompletableFuture<Void> result = eventPublisher.publishAllEvents(
                event, entityResult, 0.8, "Kiev", "50.45,30.52",
                List.of("Kiev"), -0.3, Set.of("conflict")
        );

        assertThat(result).succeedsWithin(Duration.ofSeconds(2));

        verify(kafkaTemplate, times(4)).send(anyString(), eq("test-123"), any());
    }

    @Test
    @DisplayName("Should publish entity extracted event")
    void shouldPublishEntityExtractedEvent() {
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<SendResult<String, Object>> mockFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(eq("entity-extracted"), eq("test-123"), any()))
                .thenReturn(mockFuture);

        CompletableFuture<Void> result = eventPublisher.publishEntityExtracted("test-123", entityResult);

        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should publish location detected event")
    void shouldPublishLocationDetectedEvent() {
        CompletableFuture<SendResult<String, Object>> mockFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(eq("location-detected"), eq("test-123"), any()))
                .thenReturn(mockFuture);

        CompletableFuture<Void> result = eventPublisher.publishLocationDetected(
                "test-123", "Kiev", "50.45,30.52", List.of("Kiev"), 0.9
        );

        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Should publish sentiment analyzed event")
    void shouldPublishSentimentAnalyzedEvent() {
        CompletableFuture<SendResult<String, Object>> mockFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(eq("sentiment-analyzed"), eq("test-123"), any()))
                .thenReturn(mockFuture);

        CompletableFuture<Void> result = eventPublisher.publishSentimentAnalyzed(
                "test-123", -0.3, -0.5, 0.1, -0.2, "rule-based", 0.8
        );

        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    }

    private NewsIngestedEvent createEvent() {
        return new NewsIngestedEvent(
                "test-123", "Test title", "https://test.com", "Source",
                LocalDateTime.now(), 0.6, Set.of("test"), LocalDateTime.now()
        );
    }
}
