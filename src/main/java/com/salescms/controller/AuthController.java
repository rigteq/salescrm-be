package com.salescms.controller;
import com.salescms.service.AuthService;
import com.salescms.dto.AuthDtos;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.salescms.dto.AuthDtos.AuthResponse;
import static com.salescms.dto.AuthDtos.LoginRequest;
import static com.salescms.dto.AuthDtos.SignupRequest;
import static com.salescms.dto.AuthDtos.UpdateProfileRequest;
import static com.salescms.dto.AuthDtos.UserInfo;
import static com.salescms.dto.AuthDtos.ViewAsRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserInfo me() {
        return authService.currentUser();
    }

    /** Any authenticated user can update their own profile (name, avatar, password). */
    @PutMapping("/me")
    public UserInfo updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    /**
     * Start a platform "view as" (role-template preview). Restricted to platform roles
     * (only PLATFORM_OWNER / PLATFORM_MANAGER hold PLATFORM_DASHBOARD_VIEW) and audited.
     */
    @PostMapping("/view-as")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_DASHBOARD_VIEW')")
    public void viewAs(@Valid @RequestBody ViewAsRequest request) {
        authService.recordViewAs(request.roleCode(), false);
    }

    /** Stop a platform "view as" and return to the operator's own workspace. Audited. */
    @PostMapping("/view-as/stop")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_DASHBOARD_VIEW')")
    public void stopViewAs(@Valid @RequestBody ViewAsRequest request) {
        authService.recordViewAs(request.roleCode(), true);
    }
}
