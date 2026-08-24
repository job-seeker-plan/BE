package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserProfile(
        @NotBlank String userId,
        @Min(0) long availableCash,
        @Min(0) long monthlyIncome,
        @Min(0) int age,
        @NotBlank String region,
        EmploymentStatus employmentStatus,
        @Min(0) long monthlyIncomeForPolicy,
        @Pattern(regexp = "\\d{4}-\\d{2}") String targetJobMonth
) {
}

