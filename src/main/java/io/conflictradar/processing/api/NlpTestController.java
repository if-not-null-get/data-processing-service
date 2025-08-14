package io.conflictradar.processing.api;

import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import io.conflictradar.processing.service.nlp.NlpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/nlp")
public class NlpTestController {

    private final NlpService nlpService;

    public NlpTestController(NlpService nlpService) {
        this.nlpService = nlpService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "ready", nlpService.isReady(),
                "service", "Stanford CoreNLP",
                "capabilities", Map.of(
                        "namedEntityRecognition", true,
                        "conflictEntityDetection", true,
                        "entityCaching", true
                )
        ));
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testPipeline() {
        return ResponseEntity.ok(nlpService.testPipeline());
    }

    @PostMapping("/extract")
    public ResponseEntity<EntityExtractionResult> extractEntities(@RequestBody Map<String, String> request) {
        String text = request.get("text");

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        EntityExtractionResult result = nlpService.extractEntities(text);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeText(@RequestBody Map<String, String> request) {
        String text = request.get("text");

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        EntityExtractionResult result = nlpService.extractEntities(text);

        return ResponseEntity.ok(Map.of(
                "text", text,
                "analysis", result,
                "summary", result.getSummary(),
                "conflictRelevant", result.getConflictRelevantEntities(),
                "recommendations", generateRecommendations(result)
        ));
    }

    private Map<String, Object> generateRecommendations(EntityExtractionResult result) {
        return Map.of(
                "riskLevel", calculateRiskLevel(result),
                "priority", result.hasHighPriorityConflictEntities() ? "HIGH" : "NORMAL",
                "action", result.getConflictRelevanceScore() > 0.7 ? "INVESTIGATE" : "MONITOR",
                "keyEntities", result.getConflictRelevantEntities().stream()
                        .limit(3)
                        .map(ExtractedEntity::text)
                        .toList()
        );
    }

    private String calculateRiskLevel(EntityExtractionResult result) {
        double score = result.getConflictRelevanceScore();

        if (score > 0.8) return "CRITICAL";
        if (score > 0.6) return "HIGH";
        if (score > 0.3) return "MEDIUM";
        return "LOW";
    }
}