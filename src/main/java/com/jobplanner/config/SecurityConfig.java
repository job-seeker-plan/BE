package com.jobplanner.config;

import com.jobplanner.model.AuthUser;
import com.jobplanner.service.OAuthTokenStore;
import com.jobplanner.service.OAuthUserService;
import com.jobplanner.service.OidcOAuthUserService;
import com.jobplanner.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppProperties properties,
            OAuthUserService oauthUserService,
            OidcOAuthUserService oidcOAuthUserService,
            TokenService tokenService,
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService,
            OAuthTokenStore oauthTokenStore
    ) throws Exception {
        OAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
        OAuth2AuthorizationRequestResolver googleOfflineResolver = new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return withGoogleParams(defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return withGoogleParams(defaultResolver.resolve(request, clientRegistrationId));
            }

            // Google only returns a refresh token when the authorization request asks for
            // offline access AND forces the consent screen - without this, GmailService has
            // no way to recover once the short-lived access token expires (~1 hour) and
            // every user would have to log out/in again for "메일 가져오기" to keep working.
            private OAuth2AuthorizationRequest withGoogleParams(OAuth2AuthorizationRequest authorizationRequest) {
                if (authorizationRequest == null || !authorizationRequest.getAuthorizationUri().contains("accounts.google.com")) {
                    return authorizationRequest;
                }
                return OAuth2AuthorizationRequest.from(authorizationRequest)
                        .additionalParameters(params -> {
                            params.put("access_type", "offline");
                            params.put("prompt", "consent");
                        })
                        .build();
            }
        };

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot's default error handling forwards failed requests to
                        // /error internally (e.g. a request body that fails Jackson
                        // deserialization). That forward re-enters this filter chain; without
                        // this, it fails `authenticated()` and the custom entry point below
                        // overwrites the real status (e.g. 400) with a bare 401.
                        .requestMatchers("/health", "/auth/providers", "/auth/me", "/auth/logout", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                ))
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(googleOfflineResolver))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauthUserService)
                                .oidcUserService(oidcOAuthUserService))
                        .successHandler((request, response, authentication) -> {
                            Object principal = authentication.getPrincipal();
                            String redirectTarget = properties.frontendUrl();
                            if (principal instanceof OAuth2User oAuth2User && oAuth2User.getAttribute("plannerUser") instanceof AuthUser authUser) {
                                String token = tokenService.issue(authUser);
                                redirectTarget = properties.frontendUrl() + "?token=" + token;
                                if ("google".equals(authUser.provider()) && authentication instanceof OAuth2AuthenticationToken oauthToken) {
                                    var client = authorizedClientService.loadAuthorizedClient(
                                            oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
                                    if (client != null && client.getAccessToken() != null) {
                                        String refreshToken = client.getRefreshToken() == null ? null : client.getRefreshToken().getTokenValue();
                                        oauthTokenStore.storeGoogle(authUser.userId(), client.getAccessToken().getTokenValue(), refreshToken);
                                    }
                                }
                            }
                            response.sendRedirect(redirectTarget);
                        })
                );
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(properties.frontendUrl()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
