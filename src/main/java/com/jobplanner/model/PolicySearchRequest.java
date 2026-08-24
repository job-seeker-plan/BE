package com.jobplanner.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PolicySearchRequest(
        @Min(1) int page,
        @Min(1) @Max(100) int display,
        String query,
        String bizTycdSel,
        String srchPolyBizSecd,
        String keyword,
        String srchPolicyId
) {
}

