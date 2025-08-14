package io.conflictradar.processing.service.events;

import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.output.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class ProcessingEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProcessingConfig config;

    public ProcessingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, ProcessingConfig config) {
        this.kafkaTemplate = kafkaTemplate;
        this.config = config;
    }

    /**
     * Publish comprehensive article processed event
     */
    public CompletableFuture<Void> publishArticleProcessed(
            NewsIngestedEvent originalEvent,
            EntityExtractionResult entityResult,
            double enhancedRiskScore,
            String primaryLocation,
            String coordinates,
            List<String> mentionedLocations,
            double sentimentScore,
            Set<String> categories
    ) {
        try {
            ArticleProcessedEvent event = ArticleProcessedEvent.create(
                    originalEvent.articleId(),
                    originalEvent.title(),
                    originalEvent.link(),
                    originalEvent.source(),
                    originalEvent.publishedAt(),
                    originalEvent.riskScore(),
                    originalEvent.conflictKeywords(),
                    enhancedRiskScore,
                    entityResult.getConflictRelevanceScore(),
                    entityResult.entities().size(),
                    entityResult.getConflictRelevantEntities().size(),
                    primaryLocation,
                    coordinates,
                    mentionedLocations,
                    sentimentScore,
                    categories
            );

            String topic = config.kafka().topics().output().articleProcessed();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, event.articleId(), event);

            return future.handle((result, ex) -> {
                if (ex == null) {
                    logger.info("Published article-processed event for {}: enhanced risk {:.2f}, entities {}",
                            event.articleId(), event.enhancedRiskScore(), event.totalEntities());
                } else {
                    logger.error("Failed to publish article-processed event for {}: {}",
                            event.articleId(), ex.getMessage(), ex);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("Error creating article-processed event for {}: {}",
                    originalEvent.articleId(), e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Publish entity extraction results
     */
    public CompletableFuture<Void> publishEntityExtracted(String articleId, EntityExtractionResult entityResult) {
        try {
            List<EntityExtractedEvent.ExtractedEntityInfo> entityInfos = entityResult.entities().stream()
                    .map(entity -> new EntityExtractedEvent.ExtractedEntityInfo(
                            entity.text(),
                            entity.type().name(),
                            entity.confidence(),
                            entity.isConflictRelevant(),
                            entity.getConflictPriority()
                    ))
                    .toList();

            EntityExtractedEvent event = EntityExtractedEvent.create(
                    articleId,
                    entityInfos,
                    entityResult.overallConfidence(),
                    entityResult.processingTimeMs()
            );

            String topic = config.kafka().topics().output().entityExtracted();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, event.articleId(), event);

            return future.handle((result, ex) -> {
                if (ex == null) {
                    logger.debug("Published entity-extracted event for {}: {} entities, {} conflict-relevant",
                            event.articleId(), event.totalEntities(), event.conflictRelevant());
                } else {
                    logger.error("Failed to publish entity-extracted event for {}: {}",
                            event.articleId(), ex.getMessage(), ex);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("Error creating entity-extracted event for {}: {}", articleId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Publish location detection results
     */
    public CompletableFuture<Void> publishLocationDetected(
            String articleId,
            String primaryLocation,
            String coordinates,
            List<String> allLocations,
            double confidence
    ) {
        try {
            LocationDetectedEvent event = LocationDetectedEvent.create(
                    articleId, primaryLocation, coordinates, allLocations, confidence
            );

            String topic = config.kafka().topics().output().locationDetected();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, event.articleId(), event);

            return future.handle((result, ex) -> {
                if (ex == null) {
                    logger.debug("Published location-detected event for {}: primary={}, conflicts={}",
                            event.articleId(), event.primaryLocation(), event.conflictZones());

                    // Log high-priority geographic events
                    if (!event.conflictZones().isEmpty()) {
                        logger.warn("CONFLICT ZONE detected in article {}: {}",
                                event.articleId(), event.conflictZones());
                    }
                } else {
                    logger.error("Failed to publish location-detected event for {}: {}",
                            event.articleId(), ex.getMessage(), ex);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("Error creating location-detected event for {}: {}", articleId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Publish sentiment analysis results
     */
    public CompletableFuture<Void> publishSentimentAnalyzed(
            String articleId,
            double overallSentiment,
            double violenceSentiment,
            double diplomacySentiment,
            double economySentiment,
            String approach,
            double confidence
    ) {
        try {
            SentimentAnalyzedEvent.SentimentAspects aspects =
                    new SentimentAnalyzedEvent.SentimentAspects(
                            violenceSentiment, diplomacySentiment, economySentiment, 0.0
                    );

            SentimentAnalyzedEvent event = SentimentAnalyzedEvent.create(
                    articleId, overallSentiment, aspects, approach, confidence
            );

            String topic = config.kafka().topics().output().sentimentAnalyzed();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, event.articleId(), event);

            return future.handle((result, ex) -> {
                if (ex == null) {
                    logger.debug("Published sentiment-analyzed event for {}: overall={:.2f}, violence={:.2f}",
                            event.articleId(), event.overallSentiment(), event.aspects().violence());
                } else {
                    logger.error("Failed to publish sentiment-analyzed event for {}: {}",
                            event.articleId(), ex.getMessage(), ex);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("Error creating sentiment-analyzed event for {}: {}", articleId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Publish all events for an article processing completion
     */
    public CompletableFuture<Void> publishAllEvents(
            NewsIngestedEvent originalEvent,
            EntityExtractionResult entityResult,
            double enhancedRiskScore,
            String primaryLocation,
            String coordinates,
            List<String> mentionedLocations,
            double sentimentScore,
            Set<String> categories
    ) {
        // Publish all events concurrently
        CompletableFuture<Void> articleProcessed = publishArticleProcessed(
                originalEvent, entityResult, enhancedRiskScore,
                primaryLocation, coordinates, mentionedLocations, sentimentScore, categories
        );

        CompletableFuture<Void> entitiesPublished = publishEntityExtracted(
                originalEvent.articleId(), entityResult
        );

        CompletableFuture<Void> locationPublished = publishLocationDetected(
                originalEvent.articleId(), primaryLocation, coordinates, mentionedLocations, 0.8
        );

        CompletableFuture<Void> sentimentPublished = publishSentimentAnalyzed(
                originalEvent.articleId(), sentimentScore,
                sentimentScore < -0.5 ? sentimentScore : 0.0, // violence
                Math.max(sentimentScore, 0.0),  // diplomacy
                sentimentScore * 0.3, // economy
                "rule-based", 0.7
        );

        // Wait for all events to complete
        return CompletableFuture.allOf(articleProcessed, entitiesPublished, locationPublished, sentimentPublished)
                .handle((result, ex) -> {
                    if (ex == null) {
                        logger.info("All events published successfully for article: {}", originalEvent.articleId());
                    } else {
                        logger.error("Some events failed to publish for article {}: {}",
                                originalEvent.articleId(), ex.getMessage());
                    }
                    return null;
                });
    }
}