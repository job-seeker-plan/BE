package com.jobplanner.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jobplanner.config.AppProperties;
import com.jobplanner.model.EmailEventCandidate;
import com.jobplanner.model.EmailMessageInput;
import com.jobplanner.model.EmailParseResponse;
import com.jobplanner.model.FinancialRecord;
import com.jobplanner.model.FinancialContextInput;
import com.jobplanner.model.FinancialContextListResponse;
import com.jobplanner.model.JobEvent;
import com.jobplanner.model.LinkareerRecruitmentResult;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
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
        // Job search (/jobs/search) can crawl a few Linkareer pages sequentially,
        // each launching a headless browser (see AI/app/linkareer_macro.py); 10s
        // was too tight for that and caused spurious 502s on otherwise-successful
        // searches. Other AiClient calls are simple JSON round-trips well under this.
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        // RestClient.builder() here is a plain instance, not the Spring Boot-managed
        // RestClient.Builder bean, so it does NOT inherit the app's
        // spring.jackson.property-naming-strategy=SNAKE_CASE setting. Without this,
        // Java record fields like FinancialRecord.creditScore serialize as
        // camelCase, which the AI service's snake_case Pydantic schemas either
        // reject (422) or silently ignore in favor of field defaults.
        MappingJackson2HttpMessageConverter snakeCaseJson = new MappingJackson2HttpMessageConverter(
                JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build());
        this.restClient = RestClient.builder()
                .baseUrl(properties.aiBaseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(snakeCaseJson);
                })
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

    public LinkareerRecruitmentResult searchLinkareerRecruitments(String keyword, String categoryId, String regionId, String jobType, Integer page, String regionName, String experience, Integer deadlineWithinDays) {
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
                        "limit", 20,
                        "region_name", regionName == null ? "" : regionName,
                        "experience", experience == null ? "" : experience,
                        "deadline_within_days", deadlineWithinDays == null ? 0 : deadlineWithinDays
                ))
                .retrieve()
                .body(LinkareerRecruitmentResult.class);
    }

    @SuppressWarnings("unchecked")
    // nextEvent lets the guide reason across two previously-separate agents in one
    // call: the cash-flow numbers (this method's other params) and the user's
    // nearest upcoming job-search deadline, so the generated text can explain
    // both "왜 지금 지출을 줄여야 하는지" and "왜 이 일정부터 챙겨야 하는지" together
    // instead of two disconnected pieces of advice.
    public Map<String, Object> buildFinancialGuide(String userId, String status, long targetMonthBalance, String shortageMonth, long recommendedLimit, JobEvent nextEvent) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        Map<String, Object> body = new HashMap<>(Map.of(
                "user_id", userId,
                "status", status,
                "target_month_balance", targetMonthBalance,
                "shortage_month", shortageMonth == null ? "" : shortageMonth,
                "recommended_monthly_spend_limit", recommendedLimit,
                "related_category", "cashflow"
        ));
        if (nextEvent != null) {
            body.put("next_event", Map.of(
                    "title", nextEvent.title(),
                    "event_type", nextEvent.eventType().name(),
                    "event_date", nextEvent.eventDate().toString()
            ));
        }
        return restClient.post()
                .uri("/guide")
                .header("X-Internal-Api-Key", serviceToken)
                .body(body)
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

    public List<FinancialContextInput> listFinancialContexts(String userId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        FinancialContextListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/financial-contexts").queryParam("user_id", userId).build())
                .header("X-Internal-Api-Key", serviceToken)
                .retrieve()
                .body(FinancialContextListResponse.class);
        return response == null ? List.of() : response.contexts();
    }

    public List<EmailEventCandidate> parseEmailEvents(List<EmailMessageInput> messages) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("AI_SERVICE_TOKEN is required");
        }
        if (messages.isEmpty()) {
            return List.of();
        }
        EmailParseResponse response = restClient.post()
                .uri("/email/parse-events")
                .header("X-Internal-Api-Key", serviceToken)
                .body(Map.of("messages", messages))
                .retrieve()
                .body(EmailParseResponse.class);
        return response == null ? List.of() : response.events();
    }
}
