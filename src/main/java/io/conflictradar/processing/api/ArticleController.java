package io.conflictradar.processing.api;

import io.conflictradar.processing.document.ProcessedArticleDocument;
import io.conflictradar.processing.repository.ArticleRepository;
import io.conflictradar.processing.service.elasticsearch.ElasticsearchIndexingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/articles")  // ← Бизнес-ориентированное название!
public class ArticleController {

    private final ArticleRepository articleRepository;
    private final ElasticsearchIndexingService indexingService;

    public ArticleController(ArticleRepository articleRepository, ElasticsearchIndexingService indexingService) {
        this.articleRepository = articleRepository;
        this.indexingService = indexingService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProcessedArticleDocument>> searchArticles(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {

        List<ProcessedArticleDocument> results = indexingService.searchArticles(query, limit);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/high-priority")
    public ResponseEntity<List<ProcessedArticleDocument>> getHighPriorityArticles() {
        List<ProcessedArticleDocument> articles = articleRepository.findByHighPriorityTrue();
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/conflict-relevant")
    public ResponseEntity<List<ProcessedArticleDocument>> getConflictRelevantArticles() {
        List<ProcessedArticleDocument> articles = articleRepository.findWithConflictRelevantEntities();
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/by-source/{source}")
    public ResponseEntity<List<ProcessedArticleDocument>> getArticlesBySource(
            @PathVariable String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var articles = articleRepository.findBySource(source, PageRequest.of(page, size));
        return ResponseEntity.ok(articles.getContent());
    }

    @GetMapping("/by-location/{location}")
    public ResponseEntity<List<ProcessedArticleDocument>> getArticlesByLocation(@PathVariable String location) {
        List<ProcessedArticleDocument> articles = articleRepository.findByLocation(location);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ProcessedArticleDocument>> getRecentArticles(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<ProcessedArticleDocument> articles = articleRepository.findByPublishedAtAfter(since);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/conflict-analysis")
    public ResponseEntity<List<ProcessedArticleDocument>> getConflictAnalysis(
            @RequestParam(defaultValue = "0.5") double minRelevance,
            @RequestParam(defaultValue = "48") int hoursBack) {

        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<ProcessedArticleDocument> articles = articleRepository.findConflictArticles(minRelevance, since);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getArticleStats() {
        var stats = indexingService.getStats();

        return ResponseEntity.ok(Map.of(
                "totalArticles", stats.totalArticles(),
                "highPriorityArticles", stats.highPriorityArticles(),
                "conflictRelevantArticles", stats.conflictRelevantArticles(),
                "processingStatus", Map.of(
                        "pendingInBatch", stats.pendingInBatch(),
                        "status", "active"
                )
        ));
    }
}