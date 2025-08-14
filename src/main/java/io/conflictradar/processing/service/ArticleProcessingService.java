package io.conflictradar.processing.service;

import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import io.conflictradar.processing.service.nlp.NlpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ArticleProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleProcessingService.class);

    private final NlpService nlpService;

    public ArticleProcessingService(NlpService nlpService) {
        this.nlpService = nlpService;
    }

    @KafkaListener(
            topics = "${processing.kafka.topics.news-ingested}",
            groupId = "${processing.kafka.consumer-group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processNewsArticle(
            @Payload NewsIngestedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Generate correlation ID for tracking
        String correlationId = "proc-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            logger.info("Processing article: {} from {} (topic: {}, partition: {}, offset: {})",
                    event.articleId(), event.getSimpleSource(), topic, partition, offset);

            // Process the article
            processArticle(event);

            // Manual acknowledgment after successful processing
            acknowledgment.acknowledge();

            logger.info("Successfully processed article: {}", event.articleId());

        } catch (Exception e) {
            logger.error("Failed to process article: {} - {}", event.articleId(), e.getMessage(), e);

            // TODO: Send to dead letter queue or retry logic
            // For now, acknowledge to avoid infinite retries
            acknowledgment.acknowledge();

        } finally {
            MDC.clear();
        }
    }

    private void processArticle(NewsIngestedEvent event) {
        long startTime = System.currentTimeMillis();

        logger.debug("Starting NLP processing for article: {}", event.articleId());

        // Step 1: Extract entities (persons, organizations, locations)
        EntityExtractionResult entityResult = extractEntities(event);

        // Step 2: Analyze sentiment
        analyzeSentiment(event, entityResult);

        // Step 3: Resolve geographic locations
        resolveGeography(event, entityResult);

        // Step 4: Index to Elasticsearch
        indexToElasticsearch(event, entityResult);

        // Step 5: Publish enhanced events
        publishEnhancedEvents(event, entityResult);

        long totalTime = System.currentTimeMillis() - startTime;

        logger.info("Completed processing for article: {} in {}ms (entities: {}, conflict-relevant: {})",
                event.articleId(), totalTime, entityResult.entities().size(),
                entityResult.getConflictRelevantEntities().size());
    }

    private EntityExtractionResult extractEntities(NewsIngestedEvent event) {
        logger.debug("Extracting entities from: {}", event.title());

        try {
            // Combine title and description for better entity extraction
            String textToAnalyze = event.title();

            EntityExtractionResult result = nlpService.extractEntities(textToAnalyze);

            logger.debug("Extracted {} entities from article {}: {} persons, {} organizations, {} locations",
                    result.entities().size(), event.articleId(),
                    result.getPersons().size(),
                    result.getOrganizations().size(),
                    result.getLocations().size());

            // Log high-priority conflict entities
            if (result.hasHighPriorityConflictEntities()) {
                logger.warn("High-priority conflict entities found in article {}: {}",
                        event.articleId(),
                        result.getConflictRelevantEntities().stream()
                                .map(entity -> entity.text() + "(" + entity.type() + ")")
                                .toList());
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to extract entities from article {}: {}", event.articleId(), e.getMessage(), e);
            return EntityExtractionResult.empty();
        }
    }

    private void analyzeSentiment(NewsIngestedEvent event, EntityExtractionResult entityResult) {
        logger.debug("Analyzing sentiment for: {}", event.articleId());

        try {
            // Simple rule-based sentiment analysis
            String text = event.title().toLowerCase();

            // Base sentiment from conflict keywords
            double sentimentScore = event.conflictKeywords().isEmpty() ? 0.0 :
                    -0.3 * event.conflictKeywords().size();

            // Adjust based on extracted entities
            if (entityResult.hasHighPriorityConflictEntities()) {
                sentimentScore -= 0.2;
            }

            // Boost negative sentiment for critical keywords
            if (event.isCritical()) {
                sentimentScore -= 0.3;
            }

            // Normalize to [-1.0, 1.0]
            sentimentScore = Math.max(-1.0, Math.min(1.0, sentimentScore));

            logger.debug("Sentiment analysis for {}: score = {}, entities = {}, critical = {}",
                    event.articleId(), sentimentScore, entityResult.entities().size(), event.isCritical());

        } catch (Exception e) {
            logger.error("Failed to analyze sentiment for article {}: {}", event.articleId(), e.getMessage(), e);
        }
    }

    private void resolveGeography(NewsIngestedEvent event, EntityExtractionResult entityResult) {
        logger.debug("Resolving geography for: {}", event.articleId());

        try {
            var locations = entityResult.getLocations();

            if (!locations.isEmpty()) {
                logger.debug("Found {} location entities in article {}: {}",
                        locations.size(), event.articleId(),
                        locations.stream().map(ExtractedEntity::text).toList());

                // TODO: Resolve to coordinates using GeoNames API
                // For now, just log the locations found
            }

        } catch (Exception e) {
            logger.error("Failed to resolve geography for article {}: {}", event.articleId(), e.getMessage(), e);
        }
    }

    private void indexToElasticsearch(NewsIngestedEvent event, EntityExtractionResult entityResult) {
        logger.debug("Indexing to Elasticsearch: {}", event.articleId());

        try {
            // TODO: Create enhanced article document with entities
            // For now, just log what would be indexed

            var summary = entityResult.getSummary();

            logger.debug("Would index article {} with: {} entities, conflict relevance: {:.2f}, confidence: {:.2f}",
                    event.articleId(), summary.totalEntities(), summary.conflictRelevanceScore(), summary.overallConfidence());

        } catch (Exception e) {
            logger.error("Failed to index article {} to Elasticsearch: {}", event.articleId(), e.getMessage(), e);
        }
    }

    private void publishEnhancedEvents(NewsIngestedEvent event, EntityExtractionResult entityResult) {
        logger.debug("Publishing enhanced events for: {}", event.articleId());

        try {
            // TODO: Publish to Kafka topics:
            // - article-processed (with entities and analysis)
            // - entity-extracted (individual entities)
            // - location-detected (geographic events)
            // - sentiment-analyzed (sentiment scores)

            if (entityResult.hasHighPriorityConflictEntities()) {
                logger.warn("Publishing HIGH-PRIORITY conflict event for article: {} with entities: {}",
                        event.articleId(), entityResult.getConflictRelevantEntities().size());
            }

        } catch (Exception e) {
            logger.error("Failed to publish enhanced events for article {}: {}", event.articleId(), e.getMessage(), e);
        }
    }
}