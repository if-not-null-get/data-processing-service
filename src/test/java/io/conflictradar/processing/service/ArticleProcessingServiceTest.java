package io.conflictradar.processing.service;

import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.service.elasticsearch.ElasticsearchIndexingService;
import io.conflictradar.processing.service.events.ProcessingEventPublisher;
import io.conflictradar.processing.service.geo.GeoNamesService;
import io.conflictradar.processing.service.nlp.NlpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleProcessingServiceTest {

    @Mock
    private NlpService nlpService;
    @Mock
    private ElasticsearchIndexingService elasticsearchService;
    @Mock
    private ProcessingEventPublisher eventPublisher;
    @Mock
    private GeoNamesService geoNamesService;
    @Mock
    private Acknowledgment acknowledgment;

    private ArticleProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new ArticleProcessingService(
                nlpService, elasticsearchService, eventPublisher, geoNamesService
        );
    }

    @Test
    @DisplayName("Should process article successfully and acknowledge")
    void shouldProcessArticleSuccessfullyAndAcknowledge() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        when(nlpService.extractEntities(anyString())).thenReturn(entityResult);
        when(elasticsearchService.indexArticle(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(eventPublisher.publishAllEvents(any(), any(), anyDouble(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() -> {
            processingService.processNewsArticle(event, "topic", 0, 123L, acknowledgment);
        }).doesNotThrowAnyException();

        verify(nlpService).extractEntities(event.title());
        verify(elasticsearchService).indexArticle(event, entityResult);
        verify(eventPublisher).publishAllEvents(any(), any(), anyDouble(), any(), any(), any(), anyDouble(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle NLP service exception and still acknowledge")
    void shouldHandleNlpServiceExceptionAndStillAcknowledge() {
        NewsIngestedEvent event = createEvent();

        when(nlpService.extractEntities(anyString())).thenThrow(new RuntimeException("NLP failed"));

        assertThatCode(() -> {
            processingService.processNewsArticle(event, "topic", 0, 123L, acknowledgment);
        }).doesNotThrowAnyException();

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle elasticsearch service exception and still acknowledge")
    void shouldHandleElasticsearchServiceExceptionAndStillAcknowledge() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        when(nlpService.extractEntities(anyString())).thenReturn(entityResult);
        when(elasticsearchService.indexArticle(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("ES failed")));

        assertThatCode(() -> {
            processingService.processNewsArticle(event, "topic", 0, 123L, acknowledgment);
        }).doesNotThrowAnyException();

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle event publisher exception and still acknowledge")
    void shouldHandleEventPublisherExceptionAndStillAcknowledge() {
        NewsIngestedEvent event = createEvent();
        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        when(nlpService.extractEntities(anyString())).thenReturn(entityResult);
        when(elasticsearchService.indexArticle(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(eventPublisher.publishAllEvents(any(), any(), anyDouble(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Publisher failed")));

        assertThatCode(
                () -> processingService.processNewsArticle(event, "topic", 0, 123L, acknowledgment)
        ).doesNotThrowAnyException();

        verify(acknowledgment).acknowledge();
    }

    private NewsIngestedEvent createEvent() {
        return new NewsIngestedEvent(
                "test-123", "Test article", "https://test.com", "Source",
                LocalDateTime.now(), 0.5, Set.of("test"), LocalDateTime.now()
        );
    }
}
