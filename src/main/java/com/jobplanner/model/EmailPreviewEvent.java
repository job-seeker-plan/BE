package com.jobplanner.model;

import java.time.LocalDate;

public record EmailPreviewEvent(
        String messageId,
        String title,
        JobEventType eventType,
        LocalDate eventDate,
        String memo
) {
}
