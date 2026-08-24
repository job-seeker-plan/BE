package com.jobplanner.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record JobEvent(
        String id,
        String userId,
        @NotBlank String title,
        JobEventType eventType,
        LocalDate eventDate,
        @Min(0) long expectedCost,
        String memo
) {
}

