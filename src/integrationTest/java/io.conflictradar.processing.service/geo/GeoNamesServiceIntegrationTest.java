package io.conflictradar.processing.service.geo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.conflictradar.processing.dto.nlp.ExtractedEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GeoNamesServiceIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("processing.nlp.geographic.geonames-base-url", () -> "http://localhost:8089");
        registry.add("processing.nlp.geographic.geonames-api-key", () -> "test-key");
    }

    @Autowired
    private GeoNamesService geoNamesService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Should resolve location with successful API response")
    void shouldResolveLocationWithSuccessfulApiResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/searchJSON"))
                .withQueryParam("q", equalTo("London"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {
                        "geonames": [{
                            "name": "London",
                            "countryName": "United Kingdom", 
                            "lat": 51.5074,
                            "lng": -0.1278,
                            "population": 8982000
                        }]
                    }
                    """)));

        Optional<GeoNamesService.GeoLocation> result = geoNamesService.resolveLocation("London");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("London");
        assertThat(result.get().latitude()).isEqualTo(51.5074);
    }

    @Test
    @DisplayName("Should handle API rate limit gracefully")
    void shouldHandleApiRateLimitGracefully() {
        wireMockServer.stubFor(get(urlPathEqualTo("/searchJSON"))
                .willReturn(aResponse().withStatus(429)));

        Optional<GeoNamesService.GeoLocation> result = geoNamesService.resolveLocation("TestCity");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should resolve multiple locations")
    void shouldResolveMultipleLocations() {
        wireMockServer.stubFor(get(urlPathEqualTo("/searchJSON"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"geonames\": []}")));

        List<ExtractedEntity> entities = List.of(
                new ExtractedEntity("London", ExtractedEntity.EntityType.LOCATION, 0.9, 0, 6),
                new ExtractedEntity("Paris", ExtractedEntity.EntityType.LOCATION, 0.8, 7, 12)
        );

        GeoNamesService.GeographicResolutionResult result = geoNamesService.resolveLocations(entities);

        assertThat(result).isNotNull();
    }
    }
