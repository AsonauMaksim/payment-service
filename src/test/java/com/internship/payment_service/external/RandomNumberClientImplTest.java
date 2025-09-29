package com.internship.payment_service.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.internship.payment_service.exception.RandomApiUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomNumberClientImplTest {

    private WireMockServer wm;
    private RandomNumberClientImpl client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();

        client = new RandomNumberClientImpl(WebClient.builder());

        ReflectionTestUtils.setField(client, "baseUrl", wm.baseUrl());
        ReflectionTestUtils.setField(client, "path", "/api/v1.0/random");
        ReflectionTestUtils.setField(client, "min", 1);
        ReflectionTestUtils.setField(client, "max", 100);
        ReflectionTestUtils.setField(client, "count", 1);
        ReflectionTestUtils.setField(client, "timeoutMs", 2000L);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void get_ShouldReturnNumber_WhenApiReturnsArray() {
        wm.stubFor(get(urlPathEqualTo("/api/v1.0/random"))
                .withQueryParam("min", equalTo("1"))
                .withQueryParam("max", equalTo("100"))
                .withQueryParam("count", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[42]")));

        int n = client.get();
        assertThat(n).isEqualTo(42);
    }

    @Test
    void get_ShouldThrow_WhenApiReturnsEmptyArray() {
        wm.stubFor(get(urlPathEqualTo("/api/v1.0/random"))
                .withQueryParam("min", equalTo("1"))
                .withQueryParam("max", equalTo("100"))
                .withQueryParam("count", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        assertThatThrownBy(() -> client.get())
                .isInstanceOf(RandomApiUnavailableException.class)
                .hasMessageContaining("empty array");
    }

    @Test
    void get_ShouldThrow_WhenApi500() {
        wm.stubFor(get(urlPathEqualTo("/api/v1.0/random"))
                .withQueryParam("min", equalTo("1"))
                .withQueryParam("max", equalTo("100"))
                .withQueryParam("count", equalTo("1"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.get())
                .isInstanceOf(RandomApiUnavailableException.class)
                .hasMessageContaining("failed");
    }
}
