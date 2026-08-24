package com.jobplanner.config;

import com.jobplanner.model.AuthUser;
import com.jobplanner.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// The FE cannot reliably carry the OAuth2 login session cookie across the
// localhost:5173 <-> localhost:8000 redirect boundary in every browser (Safari in
// particular drops it under some cross-port redirect chains). This filter lets the
// FE authenticate with a bearer token instead, issued once at OAuth success and
// then sent as `Authorization: Bearer <token>` on every request going forward.
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;

    public BearerTokenAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            AuthUser user = tokenService.resolve(header.substring(7));
            if (user != null) {
                SecurityContextHolder.getContext().setAuthentication(new BearerAuthenticationToken(user));
            }
        }
        filterChain.doFilter(request, response);
    }

    public static class BearerAuthenticationToken extends AbstractAuthenticationToken {
        private final AuthUser user;

        public BearerAuthenticationToken(AuthUser user) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
            this.user = user;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return user;
        }
    }
}
