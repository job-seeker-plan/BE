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
import java.util.TreeMap;

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

    public PlanAnalysis buildPlan(UserProfile profile) {
        List<FinancialRecord> records = monthlyRecordsFromTransactions(profile.userId(), store.listTransactions(profile.userId()));
        SpendPrediction spendPrediction = predictSpend(profile.userId(), records);
        long predictedSpend = spendPrediction.predictedSpend();
        long recentAverage = spendPrediction.recentAverage();
        Map<String, Long> eventCosts = aggregateEventCosts(profile.userId());
        List<String> months = monthRange(currentMonth(), profile.targetJobMonth());

        long cash = profile.availableCash();
        String shortageMonth = null;
        List<MonthlyCashFlow> flows = new ArrayList<>();
        for (String month : months) {
            long opening = cash;
            long support = 0L;
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

        GuideOutcome guideOutcome = guide(profile.userId(), status, targetBalance, shortageMonth, recommendedLimit);
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
                guideOutcome.text(),
                guideOutcome.personalized(),
                guideOutcome.contextCount()
        );
    }

    // 대시보드의 소비 예측/피드백 기능을 가계부 캘린더로 옮기면서, AI 예측 입력값도
    // 별도로 수기 입력하던 월별 금융 기록 대신 가계부에 실제로 기록된 거래 내역을
    // 월별로 집계해서 사용한다. 카드 청구/한도 관련 필드(bill/balance/creditScore)는
    // 가계부에 없는 개념이라 0/null로 채우고, AI 서비스 쪽에서 결측으로 처리한다.
    private List<FinancialRecord> monthlyRecordsFromTransactions(String userId, List<FinanceTransaction> transactions) {
        Map<String, long[]> totalsByMonth = new TreeMap<>();
        for (FinanceTransaction transaction : transactions) {
            String month = YearMonth.from(transaction.occurredOn()).toString();
            long[] totals = totalsByMonth.computeIfAbsent(month, key -> new long[2]);
            if (transaction.type() == FinanceTransactionType.expense) {
                totals[0] += transaction.amount();
            } else {
                totals[1] += transaction.amount();
            }
        }
        List<FinancialRecord> records = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totalsByMonth.entrySet()) {
            long spend = entry.getValue()[0];
            long income = entry.getValue()[1];
            records.add(new FinancialRecord(userId, entry.getKey(), spend, 0, 0, null, income));
        }
        return records;
    }

    public List<MatchedPolicy> matchPolicies(UserProfile profile) {
        return policyClient.fetchAllPolicies(new PolicySearchRequest(1, 100, null, null, "일자리", "취업", null, null)).stream()
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
        if ("전국".equals(policy.region()) || policy.regionCodes().contains(profile.region())) {
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

    private Map<String, Long> aggregateEventCosts(String userId) {
        Map<String, Long> costs = new HashMap<>();
        for (JobEvent event : store.listEvents(userId)) {
            String month = YearMonth.from(event.eventDate()).toString();
            costs.merge(month, event.expectedCost(), Long::sum);
        }
        return costs;
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

    private SpendPrediction predictSpend(String userId, List<FinancialRecord> records) {
        // A brand-new user has no ledger entries yet, so there's nothing to predict
        // from - skip the AI call entirely rather than sending it an empty history.
        if (records.isEmpty()) {
            return new SpendPrediction(0L, 0L);
        }
        try {
            Map<String, Object> response = aiClient.predictSpending(userId, records);
            return new SpendPrediction(number(response.get("predicted_next_spend")), number(response.get("recent_average_spend")));
        } catch (RuntimeException ignored) {
            // The cash-flow calculation remains useful even if the prediction
            // service is unavailable or errors out; same fallback strategy as guide().
            return new SpendPrediction(0L, 0L);
        }
    }

    private record SpendPrediction(long predictedSpend, long recentAverage) {
    }

    private GuideOutcome guide(String userId, String status, long targetBalance, String shortageMonth, long recommendedLimit) {
        try {
            Map<String, Object> response = aiClient.buildFinancialGuide(userId, status, targetBalance, shortageMonth, recommendedLimit);
            Object guide = response.get("guide");
            if (guide != null && !String.valueOf(guide).isBlank()) {
                boolean personalized = Boolean.TRUE.equals(response.get("personalized"));
                int contextCount = (int) number(response.getOrDefault("context_count", 0));
                return new GuideOutcome(String.valueOf(guide), personalized, contextCount);
            }
        } catch (RuntimeException ignored) {
            // The cash-flow calculation remains useful if the optional RAG
            // service is unavailable; use the same deterministic fallback.
        }
        String fallback;
        if ("risk".equals(status)) {
            fallback = shortageMonth + "에 자금 부족이 예상됩니다. 월 지출을 " + String.format("%,d", recommendedLimit) + "원 이하로 낮추고 정책 지원을 우선 확인하세요.";
        } else if ("caution".equals(status)) {
            fallback = "목표 취업월까지 여유가 크지 않습니다. 다음 달 예상지출 변화와 면접·시험 비용을 같이 관리하세요.";
        } else {
            fallback = "목표 취업월까지 현금흐름은 안정권입니다. 취업 일정 비용을 캘린더에서 계속 관리하세요.";
        }
        return new GuideOutcome(fallback, false, 0);
    }

    private record GuideOutcome(String text, boolean personalized, int contextCount) {
    }
}
