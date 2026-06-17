package com.salescms.platform.rbac;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RbacDtos {

    private RbacDtos() {
    }

    public record PermissionDto(UUID id, String code, String name, String description,
                                String module, boolean systemPermission) {
        static PermissionDto of(Permission permission) {
            return new PermissionDto(permission.getId(), permission.getCode(), permission.getName(),
                    permission.getDescription(), permission.getModule(), permission.isSystemPermission());
        }
    }

    public record RoleDto(UUID id, String code, String name, String description,
                          int hierarchyLevel, boolean systemRole, boolean editable,
                          UUID companyId, long userCount, long permissionCount) {
        static RoleDto of(Role role, long userCount, long permissionCount) {
            return new RoleDto(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                    role.getHierarchyLevel(), role.isSystemRole(), role.isEditable(),
                    role.getCompanyId(), userCount, permissionCount);
        }
    }

    public record RoleWithPermissionsDto(RoleDto role, List<PermissionDto> permissions) {
    }

    public record UserRoleDto(UUID userId, UUID roleId, UUID companyId, String roleCode,
                              String roleName, int hierarchyLevel, boolean primaryRole) {
    }

    public record AssignRoleRequest(UUID roleId, boolean primaryRole) {
    }

    public record UpdateRolePermissionsRequest(List<UUID> permissionIds) {
    }

    public record CreateRoleRequest(String code, String name, String description, int hierarchyLevel,
                                    UUID companyId) {
    }

    public record UpdateRoleRequest(String name, String description, Integer hierarchyLevel) {
    }

    public record CurrentUserAccessDto(UUID userId, String email, UUID companyId,
                                       List<UserRoleDto> roles, UserRoleDto primaryRole,
                                       int hierarchyLevel, Set<String> permissions,
                                       boolean canManageRoles, boolean canManageUsers,
                                       Set<String> accessibleModules) {
    }

    public record PlatformDashboardDto(long totalCompanies, long activeCompanies, long totalUsers,
                                       long totalLeads, long totalPOs, String monthlyRecurringRevenue) {
    }

    public record PlatformCompanyDto(UUID id, String name, String slug, String status, Instant createdAt) {
    }

    public record PermissionModulesDto(Map<String, List<PermissionDto>> modules) {
    }
}
