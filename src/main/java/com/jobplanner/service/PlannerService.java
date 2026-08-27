package com.jobplanner.service;

import com.jobplanner.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlannerService {
    private final PlannerStore store;
    private final AiClient aiClient;
    private final YouthPolicyClient policyClient;

    public PlannerService(PlannerStore store, AiClient aiClient, YouthPolicyClient policyClient) {
        this.store = store;
        this.aiClient = aiClient;
        this.policyClient = policyClient;
    }

    public PlanAnalysis buildPlan(UserProfile profile, ScenarioRequest scenario) {
        Map<String, Object> prediction = aiClient.predictSpending(profile.userId(), store.listRecords(profile.userId()));
        long predictedSpend = number(prediction.get("predicted_next_spend"));
        long recentAverage = number(prediction.get("recent_average_spend"));
        Map<String, Long> eventCosts = aggregateEventCosts(profile.userId(), scenario);
        Map<String, Long> policySupport = selectedPolicySupport(scenario);
        List<String> months = monthRange(currentMonth(), profile.targetJobMonth());

        long cash = profile.availableCash();
        String shortageMonth = null;
        List<MonthlyCashFlow> flows = new ArrayList<>();
        for (String month : months) {
            long opening = cash;
            long support = policySupport.getOrDefault(month, 0L);
            long eventCost = eventCosts.getOrDefault(month, 0L);
            cash = cash + profile.monthlyIncome() + support - predictedSpend - eventCost;
            if (cash < 0 && shortageMonth == null) {
                shortageMonth = month;
            }
            flows.add(new MonthlyCashFlow(month, opening, profile.monthlyIncome(), support, predictedSpend, eventCost, cash));
        }

        long targetBalance = flows.isEmpty() ? profile.availableCash() : flows.getLast().closingCash();
        long spendDelta = predictedSpend - recentAverage;
        double deltaRate = recentAverage == 0 ? 0 : Math.round((spendDelta * 1000.0 / recentAverage)) / 10.0;
        long fixedFutureCost = months.stream().mapToLong(month -> eventCosts.getOrDefault(month, 0L)).sum();
        int monthCount = Math.max(1, months.size());
        long recommendedLimit = Math.max(0, (profile.availableCash() + profile.monthlyIncome() * monthCount - fixedFutureCost) / monthCount);
        String status = targetBalance >= 500_000 ? "stable" : targetBalance >= 0 ? "caution" : "risk";
        Double monthsUntilShortage = shortageMonth == null ? (double) months.size() : (double) months.indexOf(shortageMonth) + 1;

        return new PlanAnalysis(
                predictedSpend,
                recentAverage,
                spendDelta,
                deltaRate,
                monthsUntilShortage,
                shortageMonth,
                targetBalance,
                Math.max(0, -targetBalance),
                recommendedLimit,
                status,
                flows,
                guide(status, spendDelta, shortageMonth, recommendedLimit)
        );
    }

    public List<MatchedPolicy> matchPolicies(UserProfile profile) {
        return policyClient.fetchPolicies(new PolicySearchRequest(1, 100, null, "취업", "일자리", null, null, null)).stream()
                .map(policy -> matchPolicy(policy, profile))
                .sorted(Comparator.comparing(MatchedPolicy::matchScore).reversed())
                .toList();
    }

    public List<Policy> searchPolicies(PolicySearchRequest request) {
        return policyClient.fetchPolicies(request);
    }

    private MatchedPolicy matchPolicy(Policy policy, UserProfile profile) {
        int score = 0;
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (policy.ageLimited() && policy.minAge() <= profile.age() && profile.age() <= policy.maxAge()) {
            score += 30;
            matched.add("연령 조건 일치");
        } else if (!policy.ageLimited()) {
            missing.add("연령 조건 공고 확인 필요");
        } else {
            missing.add("연령 조건 불일치");
        }
        if (!policy.regionCodes().isEmpty()) {
            missing.add("지역 코드 공고 확인 필요");
        } else if ("전국".equals(policy.region()) || policy.region().contains(profile.region())) {
            score += 25;
            matched.add("지역 조건 일치");
        } else {
            missing.add("지역 조건 확인 필요");
        }
        if (policy.employmentCode().isBlank()) {
            missing.add("취업상태 조건 공고 확인 필요");
        } else if (policy.employmentStatus() == EmploymentStatus.any || policy.employmentStatus() == profile.employmentStatus()) {
            score += 25;
            matched.add("취업상태 조건 일치");
        } else {
            missing.add("취업상태 조건 불일치");
        }
        if (policy.incomeTypeCode().isBlank()) {
            missing.add("소득 조건 공고 확인 필요");
        } else if (policy.incomeLimit() == null || profile.monthlyIncomeForPolicy() <= policy.incomeLimit()) {
            score += 20;
            matched.add("소득 조건 일치");
        } else {
            missing.add("소득 조건 초과");
        }
        return MatchedPolicy.from(policy, score, matched, missing);
    }

    private Map<String, Long> aggregateEventCosts(String userId, ScenarioRequest scenario) {
        Map<String, Long> costs = new HashMap<>();
        for (JobEvent event : store.listEvents(userId)) {
            String month = YearMonth.from(event.eventDate()).toString();
            costs.merge(month, event.expectedCost(), Long::sum);
        }
        if (scenario != null && scenario.extraMonth() != null && scenario.extraCost() > 0) {
            costs.merge(scenario.extraMonth(), scenario.extraCost(), Long::sum);
        }
        return costs;
    }

    private Map<String, Long> selectedPolicySupport(ScenarioRequest scenario) {
        if (scenario == null || scenario.confirmedSupportAmount() == 0 || scenario.confirmedSupportMonth() == null
                || scenario.policyIds() == null || scenario.policyIds().isEmpty()) {
            return Map.of();
        }
        return Map.of(scenario.confirmedSupportMonth(), scenario.confirmedSupportAmount());
    }

    private List<String> monthRange(String start, String end) {
        YearMonth cursor = YearMonth.parse(start);
        YearMonth target = YearMonth.parse(end);
        List<String> months = new ArrayList<>();
        while (!cursor.isAfter(target)) {
            months.add(cursor.toString());
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private String currentMonth() {
        return YearMonth.from(LocalDate.now()).toString();
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String guide(String status, long spendDelta, String shortageMonth, long recommendedLimit) {
        if ("risk".equals(status)) {
            return shortageMonth + "에 자금 부족이 예상됩니다. 월 지출을 " + String.format("%,d", recommendedLimit) + "원 이하로 낮추고 정책 지원을 우선 확인하세요.";
        }
        if ("caution".equals(status)) {
            return "목표 취업월까지 여유가 크지 않습니다. 다음 달 예상지출 변화와 면접·시험 비용을 같이 관리하세요.";
        }
        return "목표 취업월까지 현금흐름은 안정권입니다. 취업 일정 비용을 캘린더에서 계속 관리하세요.";
    }
}
