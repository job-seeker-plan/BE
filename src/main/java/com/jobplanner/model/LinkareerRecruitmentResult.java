package com.jobplanner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LinkareerRecruitmentResult(
        List<LinkareerRecruitment> jobs,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("total_count") int totalCount,
        int page,
        @JsonProperty("page_size") int pageSize,
        boolean cached
) {
}
