package io.conflictradar.processing.service;

import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"news-ingested-test"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@ActiveProfiles("test")
@DirtiesContext
public class ArticleProcessingServiceIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ArticleProcessingService processingService;

    private NewsIngestedEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new NewsIngestedEvent(
                "test-article-123",
                "War escalates in conflict zone",
                "https://example.com/news/war-escalates",
                "BBC",
                LocalDateTime.now().minusHours(1),
                0.75,
                Set.of("war", "conflict", "violence"),
                LocalDateTime.now()
        );
    }

    @Test
    void shouldProcessNewsIngestedEvent() {
        kafkaTemplate.send("news-ingested-test", testEvent.articleId(), testEvent);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // TODO: verify processing completed
                    // For now, just check no exceptions thrown
                });
    }

    @Test
    void shouldHandleHighRiskArticle() {
        var highRiskEvent = new NewsIngestedEvent(
                "critical-123",
                "Nuclear threat and terrorism alert",
                "https://example.com/critical",
                "Reuters",
                LocalDateTime.now(),
                0.95,
                Set.of("nuclear", "terrorism", "threat"),
                LocalDateTime.now()
        );

        kafkaTemplate.send("news-ingested-test", highRiskEvent.articleId(), highRiskEvent);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // TODO: verify high-risk processing
                });
    }
}
