package com.jobplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        String aiBaseUrl,
        String aiServiceToken,
        String govApiKey,
        String googleClientId,
        String googleClientSecret,
        String kakaoClientId,
        String kakaoClientSecret,
        String naverClientId,
        String naverClientSecret
) {
    public boolean isGoogleConfigured() {
        return configured(googleClientId, googleClientSecret);
    }

    public boolean isKakaoConfigured() {
        return configured(kakaoClientId, kakaoClientSecret);
    }

    public boolean isNaverConfigured() {
        return configured(naverClientId, naverClientSecret);
    }

    private boolean configured(String clientId, String clientSecret) {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
