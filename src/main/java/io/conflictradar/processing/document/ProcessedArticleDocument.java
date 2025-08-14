package io.conflictradar.processing.document;

import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Document(indexName = "articles-#{T(java.time.LocalDate).now().toString().substring(0,7)}")  // articles-2025-08
@Setting(settingPath = "elasticsearch/article-settings.json")
@Mapping(mappingPath = "elasticsearch/article-mapping.json")
public record ProcessedArticleDocument(

        @Id
        String id,

        @Field(type = FieldType.Text, analyzer = "conflict_analyzer")
        String title,

        @Field(type = FieldType.Text, analyzer = "conflict_analyzer")
        String description,

        @Field(type = FieldType.Keyword)
        String link,

        @Field(type = FieldType.Keyword)
        String source,

        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
        LocalDateTime publishedAt,

        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
        LocalDateTime processedAt,

        @Field(type = FieldType.Double)
        double originalRiskScore,

        @Field(type = FieldType.Double)
        double enhancedRiskScore,

        @Field(type = FieldType.Keyword)
        Set<String> conflictKeywords,

        @Field(type = FieldType.Nested)
        List<ExtractedEntityDocument> entities,

        @Field(type = FieldType.Object)
        GeographicInfo geographic,

        @Field(type = FieldType.Object)
        SentimentInfo sentiment,

        @Field(type = FieldType.Keyword)
        Set<String> categories,

        @Field(type = FieldType.Double)
        double conflictRelevanceScore,

        @Field(type = FieldType.Boolean)
        boolean highPriority

) {

    public record ExtractedEntityDocument(
            @Field(type = FieldType.Keyword)
            String text,

            @Field(type = FieldType.Keyword)
            String type,

            @Field(type = FieldType.Double)
            double confidence,

            @Field(type = FieldType.Integer)
            int startPosition,

            @Field(type = FieldType.Integer)
            int endPosition,

            @Field(type = FieldType.Boolean)
            boolean conflictRelevant
    ) {
        public static ExtractedEntityDocument from(ExtractedEntity entity) {
            return new ExtractedEntityDocument(
                    entity.text(),
                    entity.type().name(),
                    entity.confidence(),
                    entity.startPosition(),
                    entity.endPosition(),
                    entity.isConflictRelevant()
            );
        }
    }

    public record GeographicInfo(
            @Field(type = FieldType.Keyword)
            String primaryLocation,

            @GeoPointField
            String coordinates,  // "lat,lon" format

            @Field(type = FieldType.Keyword)
            List<String> mentionedLocations,

            @Field(type = FieldType.Double)
            double confidence
    ) {}

    public record SentimentInfo(
            @Field(type = FieldType.Double)
            double overall,

            @Field(type = FieldType.Double)
            double violence,

            @Field(type = FieldType.Double)
            double diplomacy,

            @Field(type = FieldType.Double)
            double economy
    ) {}

    /**
     * Factory method to create from processing results
     */
    public static ProcessedArticleDocument create(
            String articleId,
            String title,
            String description,
            String link,
            String source,
            LocalDateTime publishedAt,
            double originalRiskScore,
            Set<String> conflictKeywords,
            List<ExtractedEntity> entities,
            double enhancedRiskScore,
            double conflictRelevanceScore
    ) {

        // Convert entities
        List<ExtractedEntityDocument> entityDocs = entities.stream()
                .map(ExtractedEntityDocument::from)
                .toList();

        // Extract geographic info (simplified for now)
        List<String> locations = entities.stream()
                .filter(e -> e.type() == ExtractedEntity.EntityType.LOCATION)
                .map(ExtractedEntity::text)
                .toList();

        GeographicInfo geographic = new GeographicInfo(
                locations.isEmpty() ? null : locations.get(0),
                null, // TODO: resolve coordinates
                locations,
                locations.isEmpty() ? 0.0 : 0.8
        );

        // Simple sentiment analysis (rule-based)
        double sentimentScore = conflictKeywords.isEmpty() ? 0.0 :
                -0.3 * Math.min(conflictKeywords.size(), 3);

        SentimentInfo sentiment = new SentimentInfo(
                sentimentScore,
                conflictKeywords.contains("violence") ? -0.8 : 0.0,
                conflictKeywords.contains("diplomacy") ? 0.3 : 0.0,
                conflictKeywords.contains("economy") ? -0.2 : 0.0
        );

        // Determine categories
        Set<String> categories = Set.of("conflict", "news");
        if (entities.stream().anyMatch(e -> e.type() == ExtractedEntity.EntityType.PERSON)) {
            categories = Set.of("conflict", "news", "political");
        }

        return new ProcessedArticleDocument(
                articleId,
                title,
                description,
                link,
                source,
                publishedAt,
                LocalDateTime.now(),
                originalRiskScore,
                enhancedRiskScore,
                conflictKeywords,
                entityDocs,
                geographic,
                sentiment,
                categories,
                conflictRelevanceScore,
                enhancedRiskScore > 0.7 || conflictRelevanceScore > 0.6
        );
    }
}