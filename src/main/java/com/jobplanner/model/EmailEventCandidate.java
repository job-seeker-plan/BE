package com.jobplanner.model;

// Raw shape returned by the AI service - kept as plain strings (not JobEventType/LocalDate)
// so a value the AI side didn't validate as expected never fails Jackson deserialization;
// ApiController coerces/validates when turning this into an EmailPreviewEvent.
public record EmailEventCandidate(
        String messageId,
        String title,
        String eventType,
        String eventDate,
        String memo
) {
}
