package com.jobplanner.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinancialContextRequest(
        @NotEmpty @Size(max = 8) List<@Valid FinancialContextInput> contexts
) {
}
