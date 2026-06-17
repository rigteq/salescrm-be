package com.salescms.platform.rbac;

import com.salescms.platform.auth.User;
import com.salescms.platform.auth.UserRepository;
import com.salescms.platform.common.BadRequestException;
import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.salescms.platform.rbac.RbacDtos.CurrentUserAccessDto;
import static com.salescms.platform.rbac.RbacDtos.UserRoleDto;

@Service("permissionService")
public class PermissionService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRoleRepository userRoles;
    private final RolePermissionRepository rolePermissions;
    private final PermissionRequestCache requestCache;

    public PermissionService(UserRepository users, RoleRepository roles,
                             PermissionRepository permissions, UserRoleRepository userRoles,
                             RolePermissionRepository rolePermissions,
                             PermissionRequestCache requestCache) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.requestCache = requestCache;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(String permissionCode) {
        CurrentUserAccessDto access = currentAccess();
        return isPlatformOwner(access) || access.permissions().contains(permissionCode);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyPermission(String... permissionCodes) {
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        Set<String> owned = access.permissions();
        for (String code : permissionCodes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean hasModuleAction(String moduleKey, String action) {
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        Set<String> owned = access.permissions();
        String dynamicCode = modulePermissionCode(moduleKey, action);
        if (owned.contains(dynamicCode)) {
            return true;
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "view" -> hasAnyPermission(owned, "LEAD_VIEW_ALL", "LEAD_VIEW_TEAM", "LEAD_VIEW_ASSIGNED", "PO_VIEW_ALL", "PO_VIEW_OWN");
            case "create" -> hasAnyPermission(owned, "LEAD_CREATE", "PO_CREATE");
            case "update" -> hasAnyPermission(owned, "LEAD_UPDATE", "PO_UPDATE");
            case "delete" -> hasAnyPermission(owned, "LEAD_DELETE", "PO_DELETE");
            case "assign" -> hasAnyPermission(owned, "LEAD_ASSIGN", "USER_ASSIGN_ROLE");
            case "export" -> hasAnyPermission(owned, "LEAD_EXPORT", "PO_EXPORT", "REPORT_EXPORT");
            case "import" -> owned.contains("LEAD_IMPORT");
            case "approve" -> hasAnyPermission(owned, "PO_UPDATE", "BILLING_MANAGE");
            case "configure" -> hasAnyPermission(owned, "CUSTOM_FIELD_MANAGE", "SETTINGS_UPDATE", "PLATFORM_PERMISSION_MANAGE");
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public boolean hasDynamicModuleAction(String moduleKey, String action) {
        CurrentUserAccessDto access = currentAccess();
        return isPlatformOwner(access) || access.permissions().contains(modulePermissionCode(moduleKey, action));
    }

    public String modulePermissionCode(String moduleKey, String action) {
        String module = moduleKey == null ? "" : moduleKey.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        String verb = action == null ? "" : action.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return "MODULE_" + module + "_" + verb;
    }

    @Transactional(readOnly = true)
    public boolean canManageRole(UUID targetRoleId) {
        Role target = roles.findByIdAndDeletedFalse(targetRoleId)
                .orElseThrow(() -> new NotFoundException("Role", targetRoleId));
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        if (target.getCompanyId() == null) {
            return false;
        }
        if (!target.getCompanyId().equals(TenantContext.requireTenantId())) {
            return false;
        }
        return access.hierarchyLevel() < target.getHierarchyLevel();
    }

    @Transactional(readOnly = true)
    public boolean canManageUser(UUID targetUserId) {
        User target = users.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User", targetUserId));
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        if (!target.getTenantId().equals(TenantContext.requireTenantId())) {
            return false;
        }
        return access.hierarchyLevel() < hierarchyFor(target);
    }

    @Transactional(readOnly = true)
    public boolean canAssignRole(UUID roleId) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        if (RbacCatalog.PLATFORM_OWNER.equals(role.getCode())) {
            return false;
        }
        UUID tenantId = TenantContext.requireTenantId();
        if (role.getCompanyId() != null && !role.getCompanyId().equals(tenantId)) {
            return false;
        }
        return access.hierarchyLevel() < role.getHierarchyLevel();
    }

    @Transactional(readOnly = true)
    public boolean canAssignPermission(UUID roleId, UUID permissionId) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        Permission permission = permissions.findByIdAndDeletedFalse(permissionId)
                .orElseThrow(() -> new NotFoundException("Permission", permissionId));
        CurrentUserAccessDto access = currentAccess();
        if (isPlatformOwner(access)) {
            return true;
        }
        if (!canManageRole(role.getId()) || RbacCatalog.isPlatformPermission(permission.getCode())) {
            return false;
        }
        return access.permissions().contains(permission.getCode());
    }

    @Transactional(readOnly = true)
    public boolean isPlatformOwner() {
        return isPlatformOwner(currentAccess());
    }

    private boolean isPlatformOwner(CurrentUserAccessDto access) {
        return access.roles().stream()
                .anyMatch(role -> RbacCatalog.PLATFORM_OWNER.equals(role.roleCode()));
    }

    @Transactional(readOnly = true)
    public boolean isCompanyOwner() {
        return currentAccess().roles().stream()
                .anyMatch(role -> RbacCatalog.COMPANY_OWNER.equals(role.roleCode()));
    }

    @Transactional(readOnly = true)
    public boolean isCompanyAdmin() {
        return currentAccess().roles().stream()
                .anyMatch(role -> RbacCatalog.COMPANY_ADMIN.equals(role.roleCode()));
    }

    @Transactional(readOnly = true)
    public int getCurrentUserHierarchyLevel() {
        return currentAccess().hierarchyLevel();
    }

    @Transactional(readOnly = true)
    public Set<String> getCurrentUserPermissions() {
        return currentAccess().permissions();
    }

    @Transactional(readOnly = true)
    public CurrentUserAccessDto currentAccess() {
        User user = currentUser();
        return accessFor(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserAccessDto accessFor(User user) {
        return requestCache.getAccess(user.getId(), () -> buildAccess(user));
    }

    private CurrentUserAccessDto buildAccess(User user) {
        List<UserRoleDto> roleDtos = assignedRoleDtos(user);
        UserRoleDto primary = roleDtos.stream()
                .filter(UserRoleDto::primaryRole)
                .findFirst()
                .orElse(roleDtos.isEmpty() ? null : roleDtos.get(0));
        int hierarchy = primary != null ? primary.hierarchyLevel() : 100;
        Set<Permission> resolvedPermissions = permissionsForRoles(roleDtos);
        Set<String> codes = resolvedPermissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> modules = resolvedPermissions.stream()
                .map(Permission::getModule)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new CurrentUserAccessDto(user.getId(), user.getEmail(), user.getTenantId(),
                roleDtos, primary, hierarchy, codes,
                codes.contains("ROLE_VIEW") || codes.contains("ROLE_ASSIGN_PERMISSION") || isPlatformRole(roleDtos),
                codes.contains("USER_VIEW") || codes.contains("USER_ASSIGN_ROLE") || isPlatformRole(roleDtos),
                modules);
    }

    @Transactional(readOnly = true)
    public List<Role> assignedRoles(User user) {
        List<UserRoleDto> accessRoles = assignedRoleDtos(user);
        if (!accessRoles.isEmpty()) {
            Map<UUID, Role> byId = roles.findAllById(accessRoles.stream().map(UserRoleDto::roleId).toList())
                    .stream()
                    .filter(role -> !role.isDeleted())
                    .collect(Collectors.toMap(Role::getId, Function.identity()));
            return accessRoles.stream()
                    .map(accessRole -> byId.get(accessRole.roleId()))
                    .filter(role -> role != null)
                    .sorted(Comparator.comparingInt(Role::getHierarchyLevel))
                    .toList();
        }
        Role fallback = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(user.getRole())
                .orElse(null);
        return fallback == null ? List.of() : List.of(fallback);
    }

    @Transactional(readOnly = true)
    public int hierarchyFor(User user) {
        List<UserRoleDto> accessRoles = assignedRoleDtos(user);
        if (!accessRoles.isEmpty()) {
            return accessRoles.stream()
                    .mapToInt(UserRoleDto::hierarchyLevel)
                    .min()
                    .orElse(100);
        }
        return roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(user.getRole())
                .map(Role::getHierarchyLevel)
                .orElse(100);
    }

    public void requireCanManageRole(UUID roleId) {
        if (!canManageRole(roleId)) {
            throw new BadRequestException("You cannot manage a role with equal or higher hierarchy.");
        }
    }

    public void requireCanAssignRole(UUID roleId) {
        if (!canAssignRole(roleId)) {
            throw new BadRequestException("You cannot assign a role with equal or higher hierarchy.");
        }
    }

    public void requireCanAssignPermission(UUID roleId, UUID permissionId) {
        if (!canAssignPermission(roleId, permissionId)) {
            throw new BadRequestException("You cannot assign permissions you do not have.");
        }
    }

    private User currentUser() {
        UUID userId = TenantContext.requireUserId();
        return requestCache.getUser(userId,
                () -> users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId)));
    }

    private boolean hasAnyPermission(Set<String> owned, String... permissionCodes) {
        for (String code : permissionCodes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private Set<Permission> permissionsForRoles(List<UserRoleDto> roleDtos) {
        if (roleDtos.isEmpty()) {
            return Set.of();
        }
        if (isPlatformRole(roleDtos)) {
            return permissions.findByDeletedFalseOrderByModuleAscCodeAsc().stream()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        List<UUID> roleIds = roleDtos.stream().map(UserRoleDto::roleId).toList();
        return new LinkedHashSet<>(rolePermissions.findPermissionsForRoleIds(roleIds));
    }

    private List<UserRoleDto> assignedRoleDtos(User user) {
        List<Object[]> rows = userRoles.findAccessRoleRows(user.getId(), user.getTenantId());
        if (!rows.isEmpty()) {
            return rows.stream().map(row -> new UserRoleDto(
                            (UUID) row[0],
                            (UUID) row[1],
                            (UUID) row[2],
                            (String) row[3],
                            (String) row[4],
                            ((Number) row[5]).intValue(),
                            (Boolean) row[6]))
                    .toList();
        }
        return roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(user.getRole())
                .map(role -> List.of(new UserRoleDto(user.getId(), role.getId(), user.getTenantId(),
                        role.getCode(), role.getName(), role.getHierarchyLevel(), true)))
                .orElse(List.of());
    }

    private boolean isPlatformRole(List<UserRoleDto> assignedRoles) {
        return assignedRoles.stream().anyMatch(role -> RbacCatalog.PLATFORM_OWNER.equals(role.roleCode()));
    }
}
