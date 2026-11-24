package com.blackcoat.api.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Size(max = 120, message = "Email cannot exceed 120 characters")
        @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Please provide a valid email address (e.g. advocate@example.com)")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).*$", message = "Password must contain at least one letter and one number")
        String password,

        @Size(max = 80, message = "Full name cannot exceed 80 characters")
        @Pattern(regexp = "^[\\p{L}\\p{M}\\s.'-]{0,80}$", message = "Full name contains invalid characters")
        String displayName,

        @Pattern(regexp = "^(en|hi|ta|te|bn)?$", message = "Supported languages are en, hi, ta, te, bn")
        String langPref
    ) {}

    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Size(max = 120, message = "Email cannot exceed 120 characters")
        @Email(message = "Please provide a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password cannot exceed 72 characters")
        String password
    ) {}

    public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 255, message = "Invalid refresh token format")
        String refreshToken
    ) {}

    public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserProfile user
    ) {}

    public record UserProfile(
        UUID id,
        String email,
        String displayName,
        String langPref
    ) {}
}
