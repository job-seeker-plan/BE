package com.jobplanner.service;

import com.jobplanner.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class GoogleTokenRefresher {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final AppProperties properties;
    private final RestClient restClient = RestClient.create();

    public GoogleTokenRefresher(AppProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public String refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.googleClientId());
        form.add("client_secret", properties.googleClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object accessToken = response == null ? null : response.get("access_token");
            if (accessToken == null) {
                throw new GmailService.GmailTokenExpiredException("Gmail 접근 권한을 갱신하지 못했습니다. 다시 로그인해 주세요.");
            }
            return (String) accessToken;
        } catch (RestClientResponseException error) {
            throw new GmailService.GmailTokenExpiredException("Gmail 접근 권한이 만료됐습니다. 다시 로그인해 주세요.");
        }
    }
}
