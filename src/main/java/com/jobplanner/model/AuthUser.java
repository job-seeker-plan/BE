package com.jobplanner.model;

public record AuthUser(
        String userId,
        String provider,
        String email,
        String name
) {
}

