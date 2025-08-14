package io.conflictradar.processing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "processing.nlp.stanford.enable-cache", havingValue = "true", matchIfMissing = true)
public class RedisConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(6)) // 6 hours default TTL
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // Specific cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Entity extraction cache - 24 hours (expensive operation)
        cacheConfigurations.put("entityExtraction",
                defaultConfig.entryTtl(Duration.ofHours(24)));

        // Geographic resolution cache - 7 days (rarely changes)
        cacheConfigurations.put("geoResolution",
                defaultConfig.entryTtl(Duration.ofDays(7)));

        // Sentiment analysis cache - 12 hours (moderately expensive)
        cacheConfigurations.put("sentimentAnalysis",
                defaultConfig.entryTtl(Duration.ofHours(12)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Bean
    public CacheResolver conditionalCacheResolver(CacheManager cacheManager, ProcessingConfig config) {
        return (context) -> {
            if (!config.nlp().stanford().enableCache()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(cacheManager.getCache("entityExtraction"));
        };
    }
}
