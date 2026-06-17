package com.salescms.platform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank String companyName,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthResponse(String token, UserInfo user) {
    }

    public record UserInfo(UUID id, UUID tenantId, String tenantName,
                           String email, String firstName, String lastName, String role,
                           String primaryRoleCode, String primaryRoleName, int hierarchyLevel,
                           Set<String> permissions, String avatarUrl, String currency) {
    }

    public record UpdateProfileRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            String avatarUrl,
            String currentPassword,
            @Size(min = 8, max = 100) String newPassword) {
    }

    /** Platform-role "view as" request — the role template the operator wants to preview. */
    public record ViewAsRequest(@NotBlank String roleCode) {
    }
}
