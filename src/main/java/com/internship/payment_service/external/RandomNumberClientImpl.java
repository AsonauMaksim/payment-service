package com.internship.payment_service.external;

import com.internship.payment_service.exception.RandomApiUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RandomNumberClientImpl implements RandomNumberClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${random-api.path:/api/v1.0/random}")
    private String path;

    @Value("${random-api.min:1}")
    private int min;

    @Value("${random-api.max:100}")
    private int max;

    @Value("${random-api.count:1}")
    private int count;

    @Value("${random-api.base-url}")
    private String baseUrl;

    @Value("${random-api.timeout-ms:2000}")
    private long timeoutMs;

    @Override
    public int get() {
        try {
            Integer[] arr = webClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("min", min)
                            .queryParam("max", max)
                            .queryParam("count", count)
                            .build())
                    .retrieve()
                    .bodyToMono(Integer[].class)
                    .block(Duration.ofMillis(timeoutMs));

            if (arr == null || arr.length == 0 || arr[0] == null) {
                throw new RandomApiUnavailableException("Random API returned empty array");
            }

            return arr[0];

        } catch (Exception e) {
            throw new RandomApiUnavailableException("Random API call failed: " + e.getMessage(), e);
        }
    }
}