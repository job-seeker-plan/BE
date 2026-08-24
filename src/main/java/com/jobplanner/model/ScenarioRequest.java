package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ScenarioRequest(
        @Pattern(regexp = "\\d{4}-\\d{2}") String extraMonth,
        @Min(0) long extraCost,
        List<String> policyIds,
        @Pattern(regexp = "\\d{4}-\\d{2}") String confirmedSupportMonth,
        @Min(0) long confirmedSupportAmount
) {
}
