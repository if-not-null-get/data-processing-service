package io.conflictradar.processing.service.nlp;


import io.conflictradar.processing.config.ProcessingConfig;
import io.conflictradar.processing.dto.nlp.EntityExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NlpServiceTest {

    @Mock
    private ProcessingConfig config;

    private NlpService nlpService;

    @BeforeEach
    void setUp() {
        nlpService = new NlpService(config);
    }

    @Test
    @DisplayName("Should return empty result for null text")
    void shouldReturnEmptyResultForNullText() {
        EntityExtractionResult result = nlpService.extractEntities(null);

        assertThat(result.entities()).isEmpty();
        assertThat(result.overallConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return empty result for empty text")
    void shouldReturnEmptyResultForEmptyText() {
        EntityExtractionResult result = nlpService.extractEntities("");

        assertThat(result.entities()).isEmpty();
    }

    @Test
    @DisplayName("Should return false for isReady when not initialized")
    void shouldReturnFalseForIsReadyWhenNotInitialized() {
        assertThat(nlpService.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should create test pipeline response")
    void shouldCreateTestPipelineResponse() {
        var result = nlpService.testPipeline();

        assertThat(result).containsKeys("ready", "testText", "extractedEntities");
        assertThat(result.get("ready")).isEqualTo(false);
    }
}
