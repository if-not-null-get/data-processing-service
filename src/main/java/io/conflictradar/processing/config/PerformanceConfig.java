package io.conflictradar.processing.config;

import java.time.Duration;

public record PerformanceConfig(
        int threadPoolSize,
        int queueCapacity,
        Duration processingTimeout,
        boolean enableMetrics
) {}
