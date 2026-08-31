package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FinanceTransactionCreate(
        @NotNull LocalDate occurredOn,
        @NotNull FinanceTransactionType type,
        @NotBlank String category,
        @Min(0) long amount,
        String memo
) {
}
