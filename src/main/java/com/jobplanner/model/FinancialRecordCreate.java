package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record FinancialRecordCreate(
        @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        @Min(0) long spend,
        @Min(0) long bill,
        @Min(0) long balance,
        Integer creditScore,
        @Min(0) long income
) {
}

