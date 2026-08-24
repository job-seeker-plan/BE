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
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
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
