package com.jobplanner.model;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record RecruitmentSearchRequest(
        @Size(max = 80) String keyword,
        @Size(max = 20) String categoryId,
        @Size(max = 20) String regionId,
        @Size(max = 20) String jobType,
        @Min(1) Integer page
) {
}
