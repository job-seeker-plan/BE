package com.jobplanner.service;

import com.jobplanner.config.AppProperties;
import com.jobplanner.model.FinancialRecord;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AiClient {
    private final RestClient restClient;
    private final String serviceToken;

    public AiClient(AppProperties properties) {
        // Java's HttpClient defaults to attempting an HTTP/1.1->h2c upgrade on
        // plaintext connections. uvicorn doesn't support that upgrade, and the
        // client sends the POST body as an empty chunked stream while waiting on
        // it, so the AI service sees a request with no body at all. Pinning the
        // client to HTTP/1.1 skips the upgrade attempt entirely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(properties.aiBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.serviceToken = properties.aiServiceToken();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> predictSpending(String userId, List<FinancialRecord> records) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.post()
                .uri("/predict/spending")
                .header("X-Internal-Api-Key", serviceToken)
                .body(Map.of("user_id", userId, "records", records))
                .retrieve()
                .body(Map.class);
    }
}
