package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record FinanceTransaction(
        String id,
        String userId,
        LocalDate occurredOn,
        FinanceTransactionType type,
        @NotBlank String category,
        @Min(0) long amount,
        String memo
) {
}
