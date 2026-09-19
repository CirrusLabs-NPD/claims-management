package com.claire.claims.dto;

import com.claire.claims.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public final class AuthDtos {

    private AuthDtos() { }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) { }

    public record LoginResponse(
            String token,
            String username,
            String fullName,
            UserRole role,
            OffsetDateTime expiresAt) { }

    public record CurrentUser(
            String username,
            String fullName,
            UserRole role) { }
}
