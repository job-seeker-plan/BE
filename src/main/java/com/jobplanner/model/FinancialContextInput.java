package com.jobplanner.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinancialContextInput(
        @NotBlank @Size(max = 500) String text,
        @Size(max = 40) String dataType,
        @Size(max = 40) String relatedCategory,
        @Size(max = 40) String emotionTag,
        @Size(max = 20) String urgencyLevel
) {
}
