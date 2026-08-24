package com.jobplanner.model;

import java.util.List;

public record PlanAnalysis(
        long predictedNextSpend,
        long recentAverageSpend,
        long spendDelta,
        double spendDeltaRate,
        Double monthsUntilShortage,
        String shortageMonth,
        long targetMonthBalance,
        long targetShortageAmount,
        long recommendedMonthlySpendLimit,
        String status,
        List<MonthlyCashFlow> monthlyCashFlows,
        String guide
) {
}

