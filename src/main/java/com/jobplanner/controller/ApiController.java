package com.jobplanner.controller;

import com.jobplanner.model.*;
import com.jobplanner.config.AppProperties;
import com.jobplanner.service.AiClient;
import com.jobplanner.service.PlannerStore;
import com.jobplanner.service.PlannerService;
import com.jobplanner.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ApiController {
    private final PlannerStore store;
    private final PlannerService plannerService;
    private final AppProperties properties;
    private final TokenService tokenService;
    private final AiClient aiClient;

    public ApiController(PlannerStore store, PlannerService plannerService, AppProperties properties, TokenService tokenService, AiClient aiClient) {
        this.store = store;
        this.plannerService = plannerService;
        this.properties = properties;
        this.tokenService = tokenService;
        this.aiClient = aiClient;
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

    @GetMapping("/auth/me")
    public ResponseEntity<AuthUser> me(Authentication authentication) {
        AuthUser user = optionalCurrentUser(authentication);
        return user == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(user);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenService.revoke(header.substring(7));
        }
        return ResponseEntity.noContent().build();
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

    @PutMapping("/events/{id}")
    public JobEvent updateEvent(Authentication authentication, @PathVariable String id, @Valid @RequestBody JobEventCreate payload) {
        return store.updateEvent(id, currentUser(authentication).userId(), payload);
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(Authentication authentication, @PathVariable String id) {
        store.deleteEvent(id, currentUser(authentication).userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transactions")
    public List<FinanceTransaction> transactions(Authentication authentication) {
        return store.listTransactions(currentUser(authentication).userId());
    }

    @PostMapping("/transactions")
    public FinanceTransaction addTransaction(Authentication authentication, @Valid @RequestBody FinanceTransactionCreate payload) {
        return store.addTransaction(currentUser(authentication).userId(), payload);
    }

    @PutMapping("/transactions/{id}")
    public FinanceTransaction updateTransaction(Authentication authentication, @PathVariable String id, @Valid @RequestBody FinanceTransactionCreate payload) {
        return store.updateTransaction(id, currentUser(authentication).userId(), payload);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(Authentication authentication, @PathVariable String id) {
        store.deleteTransaction(id, currentUser(authentication).userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs/search")
    public LinkareerRecruitmentResult searchRecruitments(Authentication authentication, @Valid @RequestBody RecruitmentSearchRequest request) {
        currentUser(authentication);
        return aiClient.searchLinkareerRecruitments(request.keyword(), request.categoryId(), request.regionId(), request.jobType(), request.page());
    }

    @PostMapping("/financial-contexts")
    public Map<String, Object> saveFinancialContexts(Authentication authentication, @Valid @RequestBody FinancialContextRequest request) {
        AuthUser user = currentUser(authentication);
        return aiClient.saveFinancialContexts(user.userId(), request.contexts());
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
        return plannerService.buildPlan(requireProfile(authentication));
    }

    @GetMapping("/hiring/season")
    public Map<String, Object> hiringSeason(@RequestParam String company, @RequestParam String jobFamily) {
        return aiClient.hiringSeason(company, jobFamily);
    }

    @GetMapping("/hiring/companies")
    public List<Map<String, Object>> hiringCompanies(@RequestParam(defaultValue = "") String q) {
        return aiClient.hiringCompanies(q);
    }

    private UserProfile requireProfile(Authentication authentication) {
        UserProfile profile = store.getProfile(currentUser(authentication).userId());
        if (profile == null) {
            throw new IllegalStateException("Profile is not created yet");
        }
        return profile;
    }

    private AuthUser currentUser(Authentication authentication) {
        AuthUser user = optionalCurrentUser(authentication);
        if (user != null) {
            return user;
        }
        throw new IllegalStateException("Not authenticated");
    }

    private AuthUser optionalCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser authUser) {
            return authUser;
        }
        if (principal instanceof OAuth2User oAuth2User && oAuth2User.getAttribute("plannerUser") instanceof AuthUser authUser) {
            return authUser;
        }
        return null;
    }

}
