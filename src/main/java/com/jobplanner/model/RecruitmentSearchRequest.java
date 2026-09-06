package com.jobplanner.model;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record RecruitmentSearchRequest(
        @Size(max = 80) String keyword,
        @Size(max = 20) String categoryId,
        @Size(max = 20) String regionId,
        @Size(max = 20) String jobType,
        @Min(1) Integer page,
        @Size(max = 20) String regionName,
        @Size(max = 20) String experience,
        @Min(0) Integer deadlineWithinDays
) {
}
