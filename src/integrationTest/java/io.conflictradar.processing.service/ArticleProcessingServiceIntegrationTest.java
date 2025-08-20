package io.conflictradar.processing.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.repository.ArticleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"news-ingested-integration"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"}
)
@ActiveProfiles("test")
class ArticleProcessingServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("processing.kafka.topics.news-ingested", () -> "news-ingested-integration");
        registry.add("processing.nlp.geographic.geonames-base-url", () -> "http://localhost:8090");
        registry.add("processing.nlp.geographic.geonames-api-key", () -> "test-key");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleProcessingService processingService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8090);
        wireMockServer.start();

        // Mock GeoNames responses
        wireMockServer.stubFor(get(urlPathEqualTo("/searchJSON"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"geonames\": []}")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Should process complete article pipeline")
    void shouldProcessCompleteArticlePipeline() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "integration-pipeline-123",
                "Breaking news about conflict situation",
                "https://example.com/news",
                "Reuters",
                LocalDateTime.now(),
                0.8,
                Set.of("conflict", "breaking"),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-integration", event.articleId(), event);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(articleRepository.existsById("integration-pipeline-123")).isTrue();

                    var document = articleRepository.findById("integration-pipeline-123").get();
                    assertThat(document.title()).isEqualTo("Breaking news about conflict situation");
                    assertThat(document.source()).isEqualTo("Reuters");
                    assertThat(document.enhancedRiskScore()).isGreaterThanOrEqualTo(0.8);
                });
    }

    @Test
    @DisplayName("Should handle article with geographic content")
    void shouldHandleArticleWithGeographicContent() {
        wireMockServer.stubFor(get(urlPathEqualTo("/searchJSON"))
                .withQueryParam("q", equalTo("Ukraine"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {
                        "geonames": [{
                            "name": "Ukraine",
                            "countryName": "Ukraine",
                            "lat": 50.4501,
                            "lng": 30.5234,
                            "population": 44000000
                        }]
                    }
                    """)));

        NewsIngestedEvent event = new NewsIngestedEvent(
                "geo-integration-456",
                "Situation in Ukraine remains tense",
                "https://example.com/ukraine",
                "BBC",
                LocalDateTime.now(),
                0.9,
                Set.of("ukraine", "geopolitical"),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-integration", event.articleId(), event);

        await().atMost(45, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(articleRepository.existsById("geo-integration-456")).isTrue();

                    var document = articleRepository.findById("geo-integration-456").get();
                    assertThat(document.enhancedRiskScore()).isGreaterThan(0.9);
                });

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/searchJSON")));
    }

    @Test
    @DisplayName("Should handle processing failures gracefully")
    void shouldHandleProcessingFailuresGracefully() {
        // Setup API failure
        wireMockServer.resetAll();
        wireMockServer.stubFor(get("/searchJSON")
                .willReturn(aResponse().withStatus(500)));

        NewsIngestedEvent event = new NewsIngestedEvent(
                "failure-integration-789",
                "Article that may cause processing issues",
                "invalid-url",
                "",
                LocalDateTime.now(),
                0.5,
                Set.of(),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-integration", event.articleId(), event);

        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Should still process and index the article
                    assertThat(articleRepository.existsById("failure-integration-789")).isTrue();
                });
    }

    @Test
    @DisplayName("Should handle high priority conflict articles")
    void shouldHandleHighPriorityConflictArticles() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "high-priority-101",
                "Critical security alert: Military escalation detected",
                "https://example.com/critical",
                "Emergency News",
                LocalDateTime.now(),
                0.95,
                Set.of("critical", "military", "escalation", "security"),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-integration", event.articleId(), event);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(articleRepository.existsById("high-priority-101")).isTrue();

                    var document = articleRepository.findById("high-priority-101").get();
                    assertThat(document.enhancedRiskScore()).isEqualTo(1.0); // Should be capped at 1.0
                    assertThat(document.highPriority()).isTrue();
                });
    }

    @Test
    @DisplayName("Should handle articles with minimal content")
    void shouldHandleArticlesWithMinimalContent() {
        NewsIngestedEvent event = new NewsIngestedEvent(
                "minimal-content-202",
                "Brief",
                "https://test.com",
                "Test",
                LocalDateTime.now(),
                0.1,
                Set.of(),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-integration", event.articleId(), event);

        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(articleRepository.existsById("minimal-content-202")).isTrue();

                    var document = articleRepository.findById("minimal-content-202").get();

                    assertThat(document.title()).isEqualTo("Brief");
                    assertThat(document.enhancedRiskScore()).isEqualTo(0.1);
                    assertThat(document.highPriority()).isFalse();
                });
    }
}
