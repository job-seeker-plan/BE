package com.jobplanner.config;

import com.jobplanner.service.OAuthUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties, OAuthUserService oauthUserService) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/auth/providers", "/auth/csrf").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(oauthUserService))
                        .defaultSuccessUrl(properties.frontendUrl(), true)
                )
                .logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }));
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
