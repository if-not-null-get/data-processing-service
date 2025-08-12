package io.conflictradar.processing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "processing")
public record ProcessingConfig(
        KafkaConfig kafka,
        NlpConfig nlp,
        ElasticsearchConfig elasticsearch,
        PerformanceConfig performance
) {
    public record KafkaConfig(
            String consumerGroupId,
            String autoOffsetReset,
            int maxPollRecords,
            Duration pollTimeout,
            Topics topics
    ) {
        public record Topics(
                String newsIngested,
                String highRiskDetected,
                Output output
        ) {
            public record Output(
                    String articleProcessed,
                    String entityExtracted,
                    String locationDetected,
                    String sentimentAnalyzed
            ) {}
        }
    }
}
