package com.jobplanner.model;

import java.util.List;

public record LinkareerRecruitment(
        String id,
        String title,
        String company,
        List<String> categories,
        List<String> locations,
        String employmentType,
        String deadline,
        String url
) {
}
