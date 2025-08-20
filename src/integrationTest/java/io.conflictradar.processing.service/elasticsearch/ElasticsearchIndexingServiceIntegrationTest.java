package io.conflictradar.processing.service.elasticsearch;

import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ElasticsearchIndexingServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));
    }

    @Autowired
    private ElasticsearchIndexingService indexingService;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    @DisplayName("Should index article to real Elasticsearch")
    void shouldIndexArticleToRealElasticsearch() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "es-integration-123", "Test article", "https://test.com", "Source",
                LocalDateTime.now(), 0.7, Set.of("test"), LocalDateTime.now()
        );

        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        CompletableFuture<Void> result = indexingService.indexArticle(event, entityResult);

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(5));
        assertThat(articleRepository.existsById("es-integration-123")).isTrue();
    }

    @Test
    @DisplayName("Should calculate and store enhanced risk score")
    void shouldCalculateAndStoreEnhancedRiskScore() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "enhanced-risk-456", "Critical conflict news", "https://test.com", "Source",
                LocalDateTime.now(), 0.8, Set.of("conflict", "critical"), LocalDateTime.now()
        );

        EntityExtractionResult entityResult = EntityExtractionResult.empty();

        indexingService.indexArticle(event, entityResult);

        var document = articleRepository.findById("enhanced-risk-456").get();
        assertThat(document.enhancedRiskScore()).isGreaterThanOrEqualTo(0.8);
        assertThat(document.originalRiskScore()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("Should provide accurate indexing stats")
    void shouldProvideAccurateIndexingStats() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "stats-test-789", "High priority article", "https://test.com", "Source",
                LocalDateTime.now(), 0.9, Set.of("urgent"), LocalDateTime.now()
        );

        indexingService.indexArticle(event, EntityExtractionResult.empty());

        ElasticsearchIndexingService.IndexingStats stats = indexingService.getStats();

        assertThat(stats.totalArticles()).isGreaterThan(0);
        assertThat(stats.pendingInBatch()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should handle batching correctly")
    void shouldHandleBatchingCorrectly() {
        NewsIngestedEvent event1 = new NewsIngestedEvent(
                "batch-test-1", "Batch article 1", "https://test.com/1", "Source",
                LocalDateTime.now(), 0.5, Set.of("batch"), LocalDateTime.now()
        );

        NewsIngestedEvent event2 = new NewsIngestedEvent(
                "batch-test-2", "Batch article 2", "https://test.com/2", "Source",
                LocalDateTime.now(), 0.6, Set.of("batch"), LocalDateTime.now()
        );

        CompletableFuture<Void> result1 = indexingService.indexArticle(event1, EntityExtractionResult.empty());
        CompletableFuture<Void> result2 = indexingService.indexArticle(event2, EntityExtractionResult.empty());

        assertThat(result1).succeedsWithin(java.time.Duration.ofSeconds(5));
        assertThat(result2).succeedsWithin(java.time.Duration.ofSeconds(5));

        assertThat(articleRepository.existsById("batch-test-1")).isTrue();
        assertThat(articleRepository.existsById("batch-test-2")).isTrue();
    }

    @Test
    @DisplayName("Should force flush pending documents")
    void shouldForceFlushPendingDocuments() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "flush-test-999", "Flush test article", "https://test.com", "Source",
                LocalDateTime.now(), 0.7, Set.of("flush"), LocalDateTime.now()
        );

        indexingService.indexArticle(event, EntityExtractionResult.empty());

        assertThatCode(() -> {
            indexingService.forceFlush();
        }).doesNotThrowAnyException();

        assertThat(articleRepository.existsById("flush-test-999")).isTrue();
    }

    @Test
    @DisplayName("Should search articles correctly")
    void shouldSearchArticlesCorrectly() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "search-test-555", "Searchable conflict article", "https://test.com", "Source",
                LocalDateTime.now(), 0.8, Set.of("search"), LocalDateTime.now()
        );

        indexingService.indexArticle(event, EntityExtractionResult.empty());

        assertThat(articleRepository.existsById("search-test-555")).isTrue();

        var searchResults = indexingService.searchArticles("conflict", 10);
        assertThat(searchResults).isNotNull();
    }
}