package com.jobplanner.service;

import com.jobplanner.model.AuthUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// Google requests the "openid" scope, so Spring Security routes it through the OIDC
// login path (OidcAuthorizationCodeAuthenticationProvider), which uses this service
// instead of OAuthUserService — the plain .userService(...) registration only
// applies to non-OIDC providers (Kakao, Naver). Without this, Google logins never
// got the "plannerUser" attribute attached, so /auth/me could never resolve them.
@Service
public class OidcOAuthUserService extends OidcUserService {
    private final OAuthUserService oAuthUserService;

    public OidcOAuthUserService(OAuthUserService oAuthUserService) {
        this.oAuthUserService = oAuthUserService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        AuthUser normalized = oAuthUserService.normalize(provider, oidcUser.getAttributes());
        return new PlannerOidcUser(oidcUser, normalized);
    }

    private static class PlannerOidcUser extends DefaultOidcUser {
        private final AuthUser plannerUser;

        PlannerOidcUser(OidcUser delegate, AuthUser plannerUser) {
            super(delegate.getAuthorities(), delegate.getIdToken(), delegate.getUserInfo());
            this.plannerUser = plannerUser;
        }

        @Override
        public Map<String, Object> getAttributes() {
            Map<String, Object> merged = new HashMap<>(super.getAttributes());
            merged.put("plannerUser", plannerUser);
            return merged;
        }
    }
}
