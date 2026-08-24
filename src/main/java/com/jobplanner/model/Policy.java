package com.jobplanner.model;

import java.util.List;

public record Policy(
        String id,
        String name,
        String region,
        int minAge,
        int maxAge,
        EmploymentStatus employmentStatus,
        Long incomeLimit,
        Long benefitAmount,
        String benefitType,
        String description,
        String supportContent,
        String supervisingAgency,
        String operatingAgency,
        boolean ageLimited,
        String incomeTypeCode,
        Long incomeMin,
        String employmentCode,
        String educationCode,
        String specialConditionCode,
        List<String> regionCodes,
        String applyMethod,
        String applyUrl,
        String documents,
        String qualificationText,
        String restrictionText,
        String updatedAt,
        String applicationPeriod,
        String source
) {
}
