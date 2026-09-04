package com.jobplanner.model;

import java.util.List;

public record EmailParseResponse(
        List<EmailEventCandidate> events
) {
}
