package com.jobplanner.service;

import com.jobplanner.config.AppProperties;
import com.jobplanner.model.FinancialRecord;
import com.jobplanner.model.FinancialContextInput;
import com.jobplanner.model.LinkareerRecruitmentResult;
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> hiringSeason(String company, String jobFamily) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/hiring/season")
                        .queryParam("company", company)
                        .queryParam("job_family", jobFamily)
                        .build())
                .header("X-Internal-Api-Key", serviceToken)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> hiringCompanies(String query) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/hiring/companies")
                        .queryParam("q", query)
                        .build())
                .header("X-Internal-Api-Key", serviceToken)
                .retrieve()
                .body(List.class);
    }

    public LinkareerRecruitmentResult searchLinkareerRecruitments(String keyword, String categoryId, String regionId, String jobType, Integer page) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.post()
                .uri("/crawl/linkareer/recruitments")
                .header("X-Internal-Api-Key", serviceToken)
                .body(Map.of(
                        "keyword", keyword == null ? "" : keyword,
                        "category_id", categoryId == null ? "" : categoryId,
                        "region_id", regionId == null ? "" : regionId,
                        "job_type", jobType == null ? "" : jobType,
                        "page", page == null ? 1 : page,
                        "limit", 20
                ))
                .retrieve()
                .body(LinkareerRecruitmentResult.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildFinancialGuide(String userId, String status, long targetMonthBalance, String shortageMonth, long recommendedLimit) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.post()
                .uri("/guide")
                .header("X-Internal-Api-Key", serviceToken)
                .body(Map.of(
                        "user_id", userId,
                        "status", status,
                        "target_month_balance", targetMonthBalance,
                        "shortage_month", shortageMonth == null ? "" : shortageMonth,
                        "recommended_monthly_spend_limit", recommendedLimit,
                        "related_category", "cashflow"
                ))
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> saveFinancialContexts(String userId, List<FinancialContextInput> contexts) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        return restClient.post()
                .uri("/financial-contexts")
                .header("X-Internal-Api-Key", serviceToken)
                .body(Map.of("user_id", userId, "contexts", contexts))
                .retrieve()
                .body(Map.class);
    }
}
