package com.taskflow.api.dto.response;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt) {
}
