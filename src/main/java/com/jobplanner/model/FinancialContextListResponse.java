package com.jobplanner.model;

import java.util.List;

public record FinancialContextListResponse(
        List<FinancialContextInput> contexts
) {
}
