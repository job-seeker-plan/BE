package com.jobplanner.model;

public record EmailMessageInput(
        String messageId,
        String subject,
        String sender,
        String date,
        String body
) {
}
