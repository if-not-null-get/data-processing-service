package io.conflictradar.processing.service.elasticsearch;

import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.document.ProcessedArticleDocument;
import io.conflictradar.processing.dto.input.NewsIngestedEvent;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

@Service
public class ElasticsearchIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexingService.class);

    private final ArticleRepository articleRepository;
    private final ProcessingConfig config;
    private final List<ProcessedArticleDocument> batchBuffer = new ArrayList<>();

    public ElasticsearchIndexingService(ArticleRepository articleRepository, ProcessingConfig config) {
        this.articleRepository = articleRepository;
        this.config = config;
    }

    /**
     * Index single article with NLP results
     */
    public CompletableFuture<Void> indexArticle(
            NewsIngestedEvent event,
            EntityExtractionResult entityResult
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Indexing article: {} to Elasticsearch", event.articleId());

                // Calculate enhanced risk score
                double enhancedRiskScore = calculateEnhancedRiskScore(event, entityResult);

                // Create document
                ProcessedArticleDocument document = ProcessedArticleDocument.create(
                        event.articleId(),
                        event.title(),
                        "", // description not available in event
                        event.link(),
                        event.source(),
                        event.publishedAt(),
                        event.riskScore(),
                        event.conflictKeywords(),
                        entityResult.entities(),
                        enhancedRiskScore,
                        entityResult.getConflictRelevanceScore()
                );

                // Index document
                if (config.elasticsearch().indexing().batchSize() > 1) {
                    indexWithBatching(document);
                } else {
                    indexSingle(document);
                }

                logger.debug("Successfully indexed article: {} (enhanced risk: {:.2f}, entities: {})",
                        event.articleId(), enhancedRiskScore, entityResult.entities().size());

            } catch (Exception e) {
                logger.error("Failed to index article {} to Elasticsearch: {}",
                        event.articleId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Calculate enhanced risk score combining original score with NLP results
     */
    private double calculateEnhancedRiskScore(NewsIngestedEvent event, EntityExtractionResult entityResult) {
        double baseScore = event.riskScore();

        // Boost based on entity analysis
        double entityBoost = entityResult.getConflictRelevanceScore() * 0.3;

        // Boost for high-priority conflict entities
        if (entityResult.hasHighPriorityConflictEntities()) {
            entityBoost += 0.2;
        }

        // Boost for multiple conflict entities
        long conflictEntityCount = entityResult.getConflictRelevantEntities().size();
        if (conflictEntityCount > 2) {
            entityBoost += Math.min(conflictEntityCount * 0.05, 0.15);
        }

        // Combine scores
        double enhancedScore = Math.min(baseScore + entityBoost, 1.0);

        logger.debug("Enhanced risk score for {}: base={:.2f} + entity_boost={:.2f} = {:.2f}",
                event.articleId(), baseScore, entityBoost, enhancedScore);

        return enhancedScore;
    }

    /**
     * Index single document immediately
     */
    private void indexSingle(ProcessedArticleDocument document) {
        try {
            articleRepository.save(document);

            if (config.elasticsearch().indexing().enableRefresh()) {
                // Force refresh for immediate visibility (only in dev/test)
                // In production, rely on automatic refresh
            }

        } catch (Exception e) {
            logger.error("Failed to save document {} to Elasticsearch: {}",
                    document.id(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Add to batch buffer and flush when full
     */
    private synchronized void indexWithBatching(ProcessedArticleDocument document) {
        batchBuffer.add(document);

        if (batchBuffer.size() >= config.elasticsearch().indexing().batchSize()) {
            flushBatch();
        }
    }

    /**
     * Flush batch buffer to Elasticsearch
     */
    private void flushBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }

        try {
            List<ProcessedArticleDocument> toIndex = new ArrayList<>(batchBuffer);
            batchBuffer.clear();

            logger.debug("Flushing batch of {} documents to Elasticsearch", toIndex.size());

            Iterable<ProcessedArticleDocument> saved = articleRepository.saveAll(toIndex);

            long count = saved.spliterator().estimateSize();
            logger.info("Successfully indexed batch of {} documents to Elasticsearch", count);

        } catch (Exception e) {
            logger.error("Failed to flush batch to Elasticsearch: {}", e.getMessage(), e);

            // Re-add documents for retry (simple strategy)
            // In production, might want more sophisticated retry logic
        }
    }

    /**
     * Force flush any pending documents
     */
    public void forceFlush() {
        if (!batchBuffer.isEmpty()) {
            logger.info("Force flushing {} pending documents", batchBuffer.size());
            flushBatch();
        }
    }

    /**
     * Search articles by text query
     */
    public List<ProcessedArticleDocument> searchArticles(String query, int limit) {
        try {
            // Simple search implementation
            // In production, would have more sophisticated search logic
            return StreamSupport.stream(articleRepository.findAll().spliterator(), false)
                    .filter(doc -> doc.title().toLowerCase().contains(query.toLowerCase()) ||
                            (doc.description() != null && doc.description().toLowerCase().contains(query.toLowerCase())))
                    .limit(limit)
                    .toList();

        } catch (Exception e) {
            logger.error("Failed to search articles with query '{}': {}", query, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get statistics about indexed articles
     */
    public IndexingStats getStats() {
        try {
            long totalArticles = articleRepository.count();
            long highPriorityArticles = articleRepository.findByHighPriorityTrue().size();
            long conflictRelevantArticles = articleRepository.findWithConflictRelevantEntities().size();

            return new IndexingStats(
                    totalArticles,
                    highPriorityArticles,
                    conflictRelevantArticles,
                    batchBuffer.size()
            );

        } catch (Exception e) {
            logger.error("Failed to get indexing stats: {}", e.getMessage(), e);
            return new IndexingStats(0, 0, 0, batchBuffer.size());
        }
    }

    public record IndexingStats(
            long totalArticles,
            long highPriorityArticles,
            long conflictRelevantArticles,
            int pendingInBatch
    ) {}
}