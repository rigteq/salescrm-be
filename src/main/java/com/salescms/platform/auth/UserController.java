package com.salescms.platform.auth;

import com.salescms.platform.audit.AuditService;
import com.salescms.platform.common.BadRequestException;
import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.rbac.PermissionService;
import com.salescms.platform.rbac.Role;
import com.salescms.platform.rbac.RoleRepository;
import com.salescms.platform.rbac.UserRole;
import com.salescms.platform.rbac.UserRoleRepository;
import com.salescms.platform.tenancy.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.salescms.platform.rbac.RbacDtos.AssignRoleRequest;
import static com.salescms.platform.rbac.RbacDtos.UserRoleDto;

/** Read-only directory of tenant users, for owner pickers and display names. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserSummary(UUID id, String firstName, String lastName, String email, String role,
                              String avatarUrl) {
    }

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PermissionService permissionService;
    private final AuditService audit;

    public UserController(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
                          PermissionService permissionService, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('USER_VIEW','PLATFORM_USER_MANAGE')")
    public List<UserSummary> list() {
        return users.findByTenantIdAndDeletedFalseOrderByFirstNameAsc(TenantContext.requireTenantId()).stream()
                .map(u -> new UserSummary(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getRole(),
                        u.getAvatarUrl()))
                .toList();
    }

    @PostMapping("/{userId}/roles")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('USER_ASSIGN_ROLE') || @permissionService.hasPermission('PLATFORM_USER_MANAGE')")
    public UserRoleDto assignRole(@PathVariable UUID userId, @RequestBody AssignRoleRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        Role role = roles.findByIdAndDeletedFalse(request.roleId())
                .orElseThrow(() -> new NotFoundException("Role", request.roleId()));
        if (!permissionService.canManageUser(userId)) {
            throw new BadRequestException("You cannot manage a user with equal or higher hierarchy.");
        }
        permissionService.requireCanAssignRole(role.getId());
        if (!permissionService.isPlatformOwner() && !user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new BadRequestException("Cross-company access denied.");
        }
        UserRole assignment = userRoles.findByUserIdAndRoleIdAndCompanyId(userId, role.getId(), user.getTenantId())
                .orElseGet(() -> new UserRole(userId, role.getId(), user.getTenantId(), request.primaryRole()));
        if (request.primaryRole()) {
            userRoles.findByUserIdAndCompanyId(userId, user.getTenantId())
                    .forEach(existing -> {
                        existing.setPrimaryRole(false);
                        userRoles.save(existing);
                    });
            assignment.setPrimaryRole(true);
            user.setRole(role.getCode());
            users.save(user);
        }
        assignment = userRoles.save(assignment);
        audit.record("ASSIGN_ROLE", "USER", userId,
                Map.of("roleId", role.getId().toString(), "roleCode", role.getCode()));
        return new UserRoleDto(userId, role.getId(), user.getTenantId(), role.getCode(), role.getName(),
                role.getHierarchyLevel(), assignment.isPrimaryRole());
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('USER_ASSIGN_ROLE') || @permissionService.hasPermission('PLATFORM_USER_MANAGE')")
    public void removeRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        Role role = roles.findByIdAndDeletedFalse(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));
        if (!permissionService.canManageUser(userId)) {
            throw new BadRequestException("You cannot manage a user with equal or higher hierarchy.");
        }
        if (!permissionService.isPlatformOwner() && !user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new BadRequestException("Cross-company access denied.");
        }
        UserRole assignment = userRoles.findByUserIdAndRoleIdAndCompanyId(userId, roleId, user.getTenantId())
                .orElseThrow(() -> new NotFoundException("UserRole", roleId));
        if (assignment.isPrimaryRole()) {
            throw new BadRequestException("Cannot remove a user's primary role without assigning a new primary role.");
        }
        userRoles.deleteByUserIdAndRoleIdAndCompanyId(userId, roleId, user.getTenantId());
        audit.record("REMOVE_ROLE", "USER", userId,
                Map.of("roleId", role.getId().toString(), "roleCode", role.getCode()));
    }
}
