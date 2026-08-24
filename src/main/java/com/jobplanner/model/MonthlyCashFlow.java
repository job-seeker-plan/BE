package com.jobplanner.model;

public record MonthlyCashFlow(
        String month,
        long openingCash,
        long income,
        long policySupport,
        long predictedSpend,
        long jobEventCost,
        long closingCash
) {
}

