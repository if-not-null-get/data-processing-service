package io.conflictradar.processing.service.nlp;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NlpService {

    private static final Logger logger = LoggerFactory.getLogger(NlpService.class);

    private final ProcessingConfig config;
    private StanfordCoreNLP pipeline;
    private boolean isInitialized = false;

    public NlpService(ProcessingConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void initializePipeline() {
        try {
            logger.info("Initializing Stanford CoreNLP pipeline...");

            Properties props = new Properties();

            // Basic annotators for entity extraction
            String annotators = String.join(",", config.nlp().stanford().annotators());
            props.setProperty("annotators", annotators);

            // Language model
            props.setProperty("ner.language", "english");
            props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");

            // Performance settings
            props.setProperty("threads", "2");
            props.setProperty("timeout", String.valueOf(config.nlp().stanford().timeout() * 1000));

            // Memory settings
            props.setProperty("ner.useSUTime", "false"); // Disable time recognition for performance
            props.setProperty("parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");

            pipeline = new StanfordCoreNLP(props);
            isInitialized = true;

            logger.info("Stanford CoreNLP pipeline initialized successfully with annotators: {}", annotators);

        } catch (Exception e) {
            logger.error("Failed to initialize Stanford CoreNLP pipeline: {}", e.getMessage(), e);
            isInitialized = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (pipeline != null) {
            logger.info("Shutting down Stanford CoreNLP pipeline");
            // Stanford CoreNLP doesn't have explicit cleanup, but we can null the reference
            pipeline = null;
        }
    }

    public boolean isReady() {
        return isInitialized && pipeline != null;
    }

    /**
     * Extract named entities from text with caching
     */
    @Cacheable(cacheResolver = "conditionalCacheResolver", key = "#text != null ? #text.hashCode() : 'null'")
    public EntityExtractionResult extractEntities(String text) {
        if (!isReady()) {
            logger.warn("NLP pipeline not ready, returning empty results");
            return EntityExtractionResult.empty();
        }

        if (text == null || text.trim().isEmpty()) {
            return EntityExtractionResult.empty();
        }

        try {
            long startTime = System.currentTimeMillis();

            // Create annotation
            Annotation document = new Annotation(text);

            // Run pipeline
            pipeline.annotate(document);

            // Extract entities
            List<ExtractedEntity> entities = extractEntitiesFromDocument(document);

            long processingTime = System.currentTimeMillis() - startTime;

            logger.debug("Extracted {} entities from text in {}ms", entities.size(), processingTime);

            return new EntityExtractionResult(
                    entities,
                    processingTime,
                    calculateConfidenceScore(entities)
            );

        } catch (Exception e) {
            logger.error("Failed to extract entities from text: {}", e.getMessage(), e);
            return EntityExtractionResult.empty();
        }
    }

    private List<ExtractedEntity> extractEntitiesFromDocument(Annotation document) {
        List<ExtractedEntity> entities = new ArrayList<>();

        // Process each sentence
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {

            // Process each token
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {

                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                // Skip if no entity or not interesting entity type
                if (ner == null || "O".equals(ner) || !isRelevantEntityType(ner)) {
                    continue;
                }

                // Create entity
                ExtractedEntity entity = new ExtractedEntity(
                        word.trim(),
                        mapNerToEntityType(ner),
                        calculateEntityConfidence(ner),
                        token.beginPosition(),
                        token.endPosition()
                );

                entities.add(entity);
            }
        }

        // Group consecutive tokens of same entity type
        return groupConsecutiveEntities(entities);
    }

    private boolean isRelevantEntityType(String ner) {
        return Set.of("PERSON", "ORGANIZATION", "LOCATION", "COUNTRY", "CITY").contains(ner);
    }

    private ExtractedEntity.EntityType mapNerToEntityType(String ner) {
        return switch (ner) {
            case "PERSON" -> ExtractedEntity.EntityType.PERSON;
            case "ORGANIZATION" -> ExtractedEntity.EntityType.ORGANIZATION;
            case "LOCATION", "COUNTRY", "CITY" -> ExtractedEntity.EntityType.LOCATION;
            default -> ExtractedEntity.EntityType.OTHER;
        };
    }

    private double calculateEntityConfidence(String ner) {
        // Simple confidence based on entity type
        return switch (ner) {
            case "PERSON" -> 0.9;
            case "ORGANIZATION" -> 0.8;
            case "LOCATION", "COUNTRY" -> 0.85;
            case "CITY" -> 0.8;
            default -> 0.7;
        };
    }

    private List<ExtractedEntity> groupConsecutiveEntities(List<ExtractedEntity> entities) {
        if (entities.isEmpty()) {
            return entities;
        }

        List<ExtractedEntity> grouped = new ArrayList<>();
        ExtractedEntity current = null;
        StringBuilder currentText = new StringBuilder();

        for (ExtractedEntity entity : entities) {
            if (current == null) {
                current = entity;
                currentText.append(entity.text());
            } else if (current.type() == entity.type() &&
                    entity.startPosition() <= current.endPosition() + 2) {
                // Same type and close positions - group together
                currentText.append(" ").append(entity.text());
                current = new ExtractedEntity(
                        currentText.toString().trim(),
                        current.type(),
                        Math.max(current.confidence(), entity.confidence()),
                        current.startPosition(),
                        entity.endPosition()
                );
            } else {
                // Different type or far positions - add current and start new
                grouped.add(new ExtractedEntity(
                        currentText.toString().trim(),
                        current.type(),
                        current.confidence(),
                        current.startPosition(),
                        current.endPosition()
                ));
                current = entity;
                currentText = new StringBuilder(entity.text());
            }
        }

        // Add the last entity
        if (current != null) {
            grouped.add(new ExtractedEntity(
                    currentText.toString().trim(),
                    current.type(),
                    current.confidence(),
                    current.startPosition(),
                    current.endPosition()
            ));
        }

        // Remove duplicates and clean up
        return grouped.stream()
                .filter(entity -> entity.text().length() > 1) // Filter single characters
                .collect(Collectors.toMap(
                        entity -> entity.text().toLowerCase(),
                        entity -> entity,
                        (existing, replacement) -> existing.confidence() > replacement.confidence() ? existing : replacement
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(ExtractedEntity::startPosition))
                .collect(Collectors.toList());
    }

    private double calculateConfidenceScore(List<ExtractedEntity> entities) {
        if (entities.isEmpty()) {
            return 0.0;
        }

        double averageConfidence = entities.stream()
                .mapToDouble(ExtractedEntity::confidence)
                .average()
                .orElse(0.0);

        // Boost confidence if we found many entities
        double entityCountBoost = Math.min(entities.size() * 0.1, 0.3);

        return Math.min(averageConfidence + entityCountBoost, 1.0);
    }

    /**
     * Quick test method to verify NLP pipeline
     */
    public Map<String, Object> testPipeline() {
        String testText = "Vladimir Putin met with Joe Biden in Geneva. NATO and the UN Security Council discussed the situation in Ukraine.";

        EntityExtractionResult result = extractEntities(testText);

        return Map.of(
                "ready", isReady(),
                "testText", testText,
                "extractedEntities", result.entities().size(),
                "processingTime", result.processingTimeMs() + "ms",
                "confidence", result.overallConfidence(),
                "entities", result.entities()
        );
    }
}