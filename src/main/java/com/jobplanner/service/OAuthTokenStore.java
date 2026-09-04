package com.jobplanner.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// In-memory, same pattern as TokenService - fine for this prototype's single-instance
// deployment. Storing the refresh token lets GmailService recover from an expired
// access token without forcing the user to log in again (Google access tokens last
// ~1 hour; without this, "메일 가져오기" would break within an hour of every login).
@Service
public class OAuthTokenStore {
    public record GoogleTokens(String accessToken, String refreshToken) {
    }

    private final Map<String, GoogleTokens> googleTokens = new ConcurrentHashMap<>();

    public void storeGoogle(String userId, String accessToken, String refreshToken) {
        // Google only issues a refresh token on the authorization that actually shows
        // the consent screen - keep the previously stored one if this login didn't get a new one.
        String effectiveRefreshToken = refreshToken != null ? refreshToken
                : (googleTokens.containsKey(userId) ? googleTokens.get(userId).refreshToken() : null);
        googleTokens.put(userId, new GoogleTokens(accessToken, effectiveRefreshToken));
    }

    public void updateAccessToken(String userId, String accessToken) {
        GoogleTokens current = googleTokens.get(userId);
        String refreshToken = current == null ? null : current.refreshToken();
        googleTokens.put(userId, new GoogleTokens(accessToken, refreshToken));
    }

    public GoogleTokens getGoogle(String userId) {
        return googleTokens.get(userId);
    }

    public void removeGoogle(String userId) {
        googleTokens.remove(userId);
    }
}
