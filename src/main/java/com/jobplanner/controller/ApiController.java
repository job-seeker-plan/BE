package com.jobplanner.controller;

import com.jobplanner.model.*;
import com.jobplanner.config.AppProperties;
import com.jobplanner.service.PlannerStore;
import com.jobplanner.service.PlannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ApiController {
    private final PlannerStore store;
    private final PlannerService plannerService;
    private final AppProperties properties;

    public ApiController(PlannerStore store, PlannerService plannerService, AppProperties properties) {
        this.store = store;
        this.plannerService = plannerService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/auth/providers")
    public List<Map<String, Object>> authProviders() {
        return List.of(
                Map.of("key", "google", "label", "Google", "configured", properties.isGoogleConfigured()),
                Map.of("key", "kakao", "label", "Kakao", "configured", properties.isKakaoConfigured()),
                Map.of("key", "naver", "label", "Naver", "configured", properties.isNaverConfigured())
        );
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @GetMapping("/auth/me")
    public AuthUser me(Authentication authentication) {
        return currentUser(authentication);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile(Authentication authentication) {
        UserProfile profile = store.getProfile(currentUser(authentication).userId());
        return profile == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public UserProfile saveProfile(Authentication authentication, @Valid @RequestBody UserProfile payload) {
        AuthUser user = currentUser(authentication);
        UserProfile profile = new UserProfile(
                user.userId(),
                payload.availableCash(),
                payload.monthlyIncome(),
                payload.age(),
                payload.region(),
                payload.employmentStatus() == null ? EmploymentStatus.unemployed : payload.employmentStatus(),
                payload.monthlyIncomeForPolicy(),
                payload.targetJobMonth()
        );
        return store.saveProfile(profile);
    }

    @GetMapping("/events")
    public List<JobEvent> events(Authentication authentication) {
        return store.listEvents(currentUser(authentication).userId());
    }

    @PostMapping("/events")
    public JobEvent addEvent(Authentication authentication, @Valid @RequestBody JobEventCreate payload) {
        return store.addEvent(currentUser(authentication).userId(), payload);
    }

    @GetMapping("/financial-records")
    public List<FinancialRecord> records(Authentication authentication) {
        return store.listRecords(currentUser(authentication).userId());
    }

    @PostMapping("/financial-records")
    public FinancialRecord saveRecord(Authentication authentication, @Valid @RequestBody FinancialRecordCreate payload) {
        return store.upsertRecord(currentUser(authentication).userId(), payload);
    }

    @PostMapping("/policies/search")
    public List<Policy> searchPolicies(@Valid @RequestBody PolicySearchRequest request) {
        return plannerService.searchPolicies(request);
    }

    @GetMapping("/policies/matches")
    public List<MatchedPolicy> matchedPolicies(Authentication authentication) {
        return plannerService.matchPolicies(requireProfile(authentication));
    }

    @GetMapping("/plan")
    public PlanAnalysis plan(Authentication authentication) {
        return plannerService.buildPlan(requireProfile(authentication), null);
    }

    @PostMapping("/scenario")
    public PlanAnalysis scenario(Authentication authentication, @Valid @RequestBody ScenarioRequest request) {
        return plannerService.buildPlan(requireProfile(authentication), request);
    }

    private UserProfile requireProfile(Authentication authentication) {
        UserProfile profile = store.getProfile(currentUser(authentication).userId());
        if (profile == null) {
            throw new IllegalStateException("Profile is not created yet");
        }
        return profile;
    }

    private AuthUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User user)) {
            throw new IllegalStateException("Not authenticated");
        }
        Object value = user.getAttribute("plannerUser");
        if (value instanceof AuthUser authUser) {
            return authUser;
        }
        throw new IllegalStateException("Not authenticated");
    }

}
