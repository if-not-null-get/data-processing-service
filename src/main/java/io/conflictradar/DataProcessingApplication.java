package io.conflictradar;

import io.conflictradar.processing.config.ProcessingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableKafka
@EnableRetry
@EnableAsync
@EnableConfigurationProperties(ProcessingConfig.class)
@ConfigurationPropertiesScan
public class DataProcessingApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        SpringApplication.run(DataProcessingApplication.class, args);
    }
}