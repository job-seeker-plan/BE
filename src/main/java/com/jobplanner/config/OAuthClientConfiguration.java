package com.jobplanner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class OAuthClientConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AppProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (properties.isGoogleConfigured()) {
            registrations.add(registration("google", properties.googleClientId(), properties.googleClientSecret(),
                    ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "https://openidconnect.googleapis.com/v1/userinfo", "sub", List.of("openid", "email", "profile"),
                    "https://www.googleapis.com/oauth2/v3/certs"));
        }
        if (properties.isKakaoConfigured()) {
            registrations.add(registration("kakao", properties.kakaoClientId(), properties.kakaoClientSecret(),
                    ClientAuthenticationMethod.CLIENT_SECRET_POST,
                    "https://kauth.kakao.com/oauth/authorize",
                    "https://kauth.kakao.com/oauth/token",
                    "https://kapi.kakao.com/v2/user/me", "id", List.of("profile_nickname", "account_email"), null));
        }
        if (properties.isNaverConfigured()) {
            registrations.add(registration("naver", properties.naverClientId(), properties.naverClientSecret(),
                    ClientAuthenticationMethod.CLIENT_SECRET_POST,
                    "https://nid.naver.com/oauth2.0/authorize",
                    "https://nid.naver.com/oauth2.0/token",
                    "https://openapi.naver.com/v1/nid/me", "response", List.of("name", "email"), null));
        }
        return new MapClientRegistrationRepository(registrations);
    }

    private ClientRegistration registration(
            String registrationId,
            String clientId,
            String clientSecret,
            ClientAuthenticationMethod authenticationMethod,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String userNameAttribute,
            List<String> scopes,
            String jwkSetUri
    ) {
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(authenticationMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName(userNameAttribute)
                .clientName(registrationId);
        if (jwkSetUri != null) {
            builder.jwkSetUri(jwkSetUri);
        }
        return builder.build();
    }

    private static class MapClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        private final Map<String, ClientRegistration> registrations;

        MapClientRegistrationRepository(List<ClientRegistration> registrations) {
            this.registrations = registrations.stream().collect(Collectors.toUnmodifiableMap(ClientRegistration::getRegistrationId, Function.identity()));
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return registrations.get(registrationId);
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return registrations.values().iterator();
        }
    }
}
