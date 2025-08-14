package io.conflictradar.processing.repository;

import io.conflictradar.processing.document.ProcessedArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleRepository extends ElasticsearchRepository<ProcessedArticleDocument, String> {

    /**
     * Find articles by conflict relevance score
     */
    List<ProcessedArticleDocument> findByConflictRelevanceScoreGreaterThan(double threshold);

    /**
     * Find high priority articles
     */
    List<ProcessedArticleDocument> findByHighPriorityTrue();

    /**
     * Find articles by source
     */
    Page<ProcessedArticleDocument> findBySource(String source, Pageable pageable);

    /**
     * Find articles published after date
     */
    List<ProcessedArticleDocument> findByPublishedAtAfter(LocalDateTime date);

    /**
     * Find articles by enhanced risk score range
     */
    List<ProcessedArticleDocument> findByEnhancedRiskScoreBetween(double min, double max);

    /**
     * Search articles by text content
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title^2\", \"description\"]}}")
    Page<ProcessedArticleDocument> searchByText(String text, Pageable pageable);

    /**
     * Find articles with specific entity types
     */
    @Query("{\"nested\": {\"path\": \"entities\", \"query\": {\"term\": {\"entities.type\": \"?0\"}}}}")
    List<ProcessedArticleDocument> findByEntityType(String entityType);

    /**
     * Find articles mentioning specific location
     */
    @Query("{\"bool\": {\"should\": [" +
            "{\"term\": {\"geographic.primaryLocation\": \"?0\"}}," +
            "{\"terms\": {\"geographic.mentionedLocations\": [\"?0\"]}}" +
            "]}}")
    List<ProcessedArticleDocument> findByLocation(String location);

    /**
     * Find articles with conflict-relevant entities
     */
    @Query("{\"nested\": {\"path\": \"entities\", \"query\": {\"term\": {\"entities.conflictRelevant\": true}}}}")
    List<ProcessedArticleDocument> findWithConflictRelevantEntities();

    /**
     * Complex search for conflict analysis
     */
    @Query("{\"bool\": {" +
            "\"must\": [" +
            "  {\"range\": {\"conflictRelevanceScore\": {\"gte\": ?0}}}," +
            "  {\"range\": {\"publishedAt\": {\"gte\": \"?1\"}}}" +
            "]," +
            "\"should\": [" +
            "  {\"terms\": {\"categories\": [\"conflict\", \"political\"]}}," +
            "  {\"nested\": {\"path\": \"entities\", \"query\": {\"term\": {\"entities.conflictRelevant\": true}}}}" +
            "]" +
            "}}")
    List<ProcessedArticleDocument> findConflictArticles(
            double minRelevanceScore,
            LocalDateTime sinceDate
    );

    /**
     * Get articles for trend analysis
     */
    @Query("{\"bool\": {" +
            "\"must\": [" +
            "  {\"range\": {\"publishedAt\": {\"gte\": \"?0\", \"lte\": \"?1\"}}}," +
            "  {\"terms\": {\"source\": ?2}}" +
            "]," +
            "\"filter\": [" +
            "  {\"range\": {\"enhancedRiskScore\": {\"gte\": ?3}}}" +
            "]" +
            "}}")
    List<ProcessedArticleDocument> findForTrendAnalysis(
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<String> sources,
            double minRiskScore
    );
}