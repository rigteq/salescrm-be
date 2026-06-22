package com.salescms.service;
import com.salescms.repository.UserRepository;
import com.salescms.entity.User;
import com.salescms.repository.TenantRepository;
import com.salescms.event.TenantProvisionedEvent;
import com.salescms.entity.Tenant;
import com.salescms.dto.AuthDtos;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.service.PermissionService;
import com.salescms.entity.TenantSettings;
import com.salescms.repository.TenantSettingsRepository;
import com.salescms.dto.RbacCatalog;
import com.salescms.entity.Role;
import com.salescms.repository.RoleRepository;
import com.salescms.entity.UserRole;
import com.salescms.repository.UserRoleRepository;
import com.salescms.service.JwtService;
import com.salescms.entity.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static com.salescms.dto.AuthDtos.AuthResponse;
import static com.salescms.dto.AuthDtos.LoginRequest;
import static com.salescms.dto.AuthDtos.SignupRequest;
import static com.salescms.dto.AuthDtos.UserInfo;

@Service
public class AuthService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher events;
    private final TenantSettingsRepository tenantSettings;
    private final AuditService auditService;

    public AuthService(TenantRepository tenants, UserRepository users,
                       RoleRepository roles, UserRoleRepository userRoles,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       PermissionService permissionService,
                       ApplicationEventPublisher events,
                       TenantSettingsRepository tenantSettings,
                       AuditService auditService) {
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permissionService = permissionService;
        this.events = events;
        this.tenantSettings = tenantSettings;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            throw new BadRequestException("An account with this email already exists");
        }

        Tenant tenant = tenants.save(new Tenant(request.companyName(), uniqueSlug(request.companyName())));
        User admin = users.save(new User(
                tenant.getId(), email,
                passwordEncoder.encode(request.password()),
                request.firstName(), request.lastName(), RbacCatalog.COMPANY_OWNER));
        Role ownerRole = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(RbacCatalog.COMPANY_OWNER)
                .orElseThrow(() -> new NotFoundException("Role", RbacCatalog.COMPANY_OWNER));
        userRoles.save(new UserRole(admin.getId(), ownerRole.getId(), tenant.getId(), true));

        // Bind context so provisioning listeners can persist tenant-owned defaults.
        try {
            TenantContext.set(tenant.getId(), admin.getId());
            events.publishEvent(new TenantProvisionedEvent(tenant.getId(), admin.getId()));
        } finally {
            TenantContext.clear();
        }

        return new AuthResponse(jwtService.issueToken(admin), toUserInfo(admin, tenant));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase(Locale.ROOT))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BadRequestException("User is not active");
        }
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        return new AuthResponse(jwtService.issueToken(user), toUserInfo(user, tenant));
    }

    @Transactional(readOnly = true)
    public UserInfo currentUser() {
        User user = users.findById(TenantContext.requireUserId()).orElseThrow();
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        return toUserInfo(user, tenant);
    }

    @Transactional
    public UserInfo updateProfile(AuthDtos.UpdateProfileRequest request) {
        User user = users.findById(TenantContext.requireUserId()).orElseThrow();
        user.updateProfile(request.firstName().trim(), request.lastName().trim(),
                request.avatarUrl() == null || request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        users.save(user);
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        return toUserInfo(user, tenant);
    }

    /**
     * Record a platform operator starting or stopping a "view as" (role-template preview).
     * Caller authorization (PLATFORM_OWNER / PLATFORM_MANAGER) is enforced at the controller
     * via method security; this validates the target role and writes the audit trail.
     */
    @Transactional
    public void recordViewAs(String roleCode, boolean stop) {
        String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        boolean known = RbacCatalog.roles().stream().anyMatch(r -> r.code().equals(normalized));
        if (!known) {
            throw new BadRequestException("Unknown role: " + roleCode);
        }
        auditService.record(stop ? "VIEW_AS_STOP" : "VIEW_AS_START", "ROLE", null,
                java.util.Map.of("targetRole", normalized));
    }

    private UserInfo toUserInfo(User user, Tenant tenant) {
        var access = permissionService.accessFor(user);
        var primary = access.primaryRole();
        String currency = tenantSettings.findById(tenant.getId())
                .map(TenantSettings::getDefaultCurrency).orElse("USD");
        return new UserInfo(user.getId(), tenant.getId(), tenant.getName(),
                user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(),
                primary != null ? primary.roleCode() : user.getRole(),
                primary != null ? primary.roleName() : user.getRole(),
                access.hierarchyLevel(), access.permissions(),
                user.getAvatarUrl(), currency);
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "tenant";
        }
        String slug = base;
        int i = 2;
        while (tenants.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }
}
