package com.jobplanner.service;

import com.jobplanner.model.AuthUser;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OAuthUserService extends DefaultOAuth2UserService {
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        AuthUser normalized = normalize(provider, user.getAttributes());
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("plannerUser", normalized);
        attributes.put("plannerUserId", normalized.userId());
        return new DefaultOAuth2User(user.getAuthorities(), attributes, "plannerUserId");
    }

    public AuthUser normalize(String provider, Map<String, Object> attributes) {
        if ("kakao".equals(provider)) {
            Map<?, ?> account = map(attributes.get("kakao_account"));
            Map<?, ?> properties = map(attributes.get("properties"));
            String id = String.valueOf(attributes.get("id"));
            return new AuthUser("kakao:" + id, provider, text(account.get("email")), text(properties.get("nickname")));
        }
        if ("naver".equals(provider)) {
            Map<?, ?> response = map(attributes.get("response"));
            String id = text(response.get("id"));
            return new AuthUser("naver:" + id, provider, text(response.get("email")), text(response.get("name")));
        }
        String id = text(attributes.get("sub"));
        return new AuthUser("google:" + id, provider, text(attributes.get("email")), text(attributes.get("name")));
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> result ? result : Map.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

