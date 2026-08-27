package com.jobplanner.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PolicySearchRequest(
        @Min(1) int page,
        @Min(1) @Max(100) int display,
        String plcyNm,
        String plcyKywdNm,
        String lclsfNm,
        String mclsfNm,
        String zipCd,
        String plcyNo
) {
}
