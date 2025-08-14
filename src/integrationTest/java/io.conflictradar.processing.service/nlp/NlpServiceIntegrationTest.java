package io.conflictradar.processing.service.nlp;

import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
//@ActiveProfiles("test")
//@EnabledIfSystemProperty(named = "test.nlp.enabled", matches = "true")
class NLPServiceIntegrationTest {
    @Autowired
    private NlpService nlpService;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("Should extract entities from conflict-related text")
    void shouldExtractEntitiesFromConflictText() {
        // given
        String conflictText = "Vladimir Putin met with Joe Biden in Geneva to discuss the situation in Ukraine. " +
                "NATO and the UN Security Council are monitoring the developments closely.";

        // when
        EntityExtractionResult result = nlpService.extractEntities(conflictText);

        // then
        assertThat(result).isNotNull();
        assertThat(result.entities()).isNotEmpty();

        // Should find persons
        assertThat(result.getPersons())
                .isNotEmpty()
                .anyMatch(entity -> entity.text().contains("Putin") || entity.text().contains("Biden"));

        // Should find organizations
        assertThat(result.getOrganizations())
                .isNotEmpty()
                .anyMatch(entity -> entity.text().contains("NATO") || entity.text().contains("UN"));

        // Should find locations
        assertThat(result.getLocations())
                .isNotEmpty()
                .anyMatch(entity -> entity.text().contains("Geneva") || entity.text().contains("Ukraine"));

        // Should identify conflict-relevant entities
        assertThat(result.getConflictRelevantEntities()).isNotEmpty();
        assertThat(result.hasHighPriorityConflictEntities()).isTrue();

        // Should have reasonable processing time
        assertThat(result.processingTimeMs()).isLessThan(5000); // Less than 5 seconds
    }

    @Test
    @DisplayName("Should handle empty or null text gracefully")
    void shouldHandleEmptyTextGracefully() {
        // when/then
        EntityExtractionResult emptyResult = nlpService.extractEntities("");
        assertThat(emptyResult.entities()).isEmpty();

        EntityExtractionResult nullResult = nlpService.extractEntities(null);
        assertThat(nullResult.entities()).isEmpty();
    }

    @Test
    @DisplayName("Should extract military-related entities")
    void shouldExtractMilitaryEntities() {
        // given
        String militaryText = "The Pentagon announced that General Smith will lead the NATO forces. " +
                "The Ministry of Defense confirmed the deployment to Afghanistan.";

        // when
        EntityExtractionResult result = nlpService.extractEntities(militaryText);

        // then
        assertThat(result.getConflictRelevantEntities())
                .isNotEmpty()
                .anyMatch(entity -> entity.type() == ExtractedEntity.EntityType.ORGANIZATION);
    }

    @Test
    @DisplayName("Should cache repeated extractions")
    void shouldCacheRepeatedExtractions() {
        // given
        String text = "Test text for caching with Barack Obama and Washington";

        // when - first call
        long start1 = System.currentTimeMillis();
        EntityExtractionResult result1 = nlpService.extractEntities(text);
        long time1 = System.currentTimeMillis() - start1;

        // when - second call (should be cached)
        long start2 = System.currentTimeMillis();
        EntityExtractionResult result2 = nlpService.extractEntities(text);
        long time2 = System.currentTimeMillis() - start2;

        // then
        assertThat(result1.entities()).hasSize(result2.entities().size());
        // Second call should be much faster (cached)
        assertThat(time2).isLessThan(time1);
    }

    @Test
    @DisplayName("Should identify conflict zones correctly")
    void shouldIdentifyConflictZones() {
        // given
        String conflictZoneText = "Fighting continues in Donetsk and Crimea. " +
                "Tensions rise in the South China Sea near Taiwan.";

        // when
        EntityExtractionResult result = nlpService.extractEntities(conflictZoneText);

        // then
        assertThat(result.getLocations())
                .isNotEmpty()
                .allMatch(ExtractedEntity::isConflictRelevant);

        assertThat(result.getConflictRelevanceScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Should handle large text efficiently")
    void shouldHandleLargeTextEfficiently() {
        // given - simulate large article
        String largeText = "Breaking news: " +
                "Vladimir Putin met with Xi Jinping in Beijing to discuss trade relations. " +
                "The European Union expressed concerns about the military exercises near Ukraine. " +
                "NATO Secretary General spoke with the UN Secretary General about peacekeeping efforts. " +
                "The Pentagon is monitoring the situation in the South China Sea. " +
                "China's Ministry of Defense responded to allegations about Taiwan. " +
                "Russia's Foreign Ministry issued a statement about diplomatic talks.";

        // when
        long startTime = System.currentTimeMillis();
        EntityExtractionResult result = nlpService.extractEntities(largeText);
        long processingTime = System.currentTimeMillis() - startTime;

        // then
        assertThat(result.entities()).hasSizeGreaterThan(5);
        assertThat(processingTime).isLessThan(10000); // Less than 10 seconds
        assertThat(result.getConflictRelevantEntities()).isNotEmpty();

        // Should find multiple types of entities
        assertThat(result.getPersons()).isNotEmpty();
        assertThat(result.getOrganizations()).isNotEmpty();
        assertThat(result.getLocations()).isNotEmpty();
    }
}