package com.salescms.controller;
import com.salescms.repository.UserRoleRepository;
import com.salescms.repository.RoleRepository;
import com.salescms.repository.RolePermissionRepository;
import com.salescms.entity.RolePermission;
import com.salescms.entity.Role;
import com.salescms.dto.RbacDtos;
import com.salescms.dto.RbacCatalog;
import com.salescms.service.PermissionService;
import com.salescms.repository.PermissionRepository;
import com.salescms.entity.Permission;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.mapper.SalesCmsMapper;
import com.salescms.entity.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.salescms.dto.RbacDtos.CreateRoleRequest;
import static com.salescms.dto.RbacDtos.RoleWithPermissionsDto;
import static com.salescms.dto.RbacDtos.UpdateRolePermissionsRequest;
import static com.salescms.dto.RbacDtos.UpdateRoleRequest;

@RestController
@RequestMapping("/api/roles")
public class RoleManagementController {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final UserRoleRepository userRoles;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final SalesCmsMapper mapper;

    public RoleManagementController(RoleRepository roles, PermissionRepository permissions,
                                    RolePermissionRepository rolePermissions,
                                    UserRoleRepository userRoles, PermissionService permissionService,
                                    AuditService audit, SalesCmsMapper mapper) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.userRoles = userRoles;
        this.permissionService = permissionService;
        this.audit = audit;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('ROLE_VIEW','PLATFORM_ROLE_MANAGE')")
    public List<RbacDtos.RoleDto> list(@RequestParam(required = false) UUID companyId) {
        List<Role> visible;
        if (permissionService.isPlatformOwner()) {
            if (companyId != null) {
                visible = roles.findAssignableCatalogForCompany(companyId);
            } else {
                visible = roles.findByDeletedFalseOrderByHierarchyLevelAscNameAsc();
            }
        } else {
            visible = roles.findAssignableCatalogForCompany(TenantContext.requireTenantId());
        }
        return visible.stream().map(this::toDto).toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('ROLE_CREATE') || @permissionService.hasPermission('PLATFORM_ROLE_MANAGE')")
    public RbacDtos.RoleDto create(@Valid @RequestBody CreateRoleRequest request) {
        String code = normalizeCode(request.code());
        UUID companyId = permissionService.isPlatformOwner() ? request.companyId() : TenantContext.requireTenantId();
        if (!permissionService.isPlatformOwner()
                && request.hierarchyLevel() <= permissionService.getCurrentUserHierarchyLevel()) {
            throw new BadRequestException("You cannot create a role with equal or higher hierarchy.");
        }
        ensureUniqueRoleCode(code, companyId, null);
        Role role = roles.save(new Role(code, request.name(), request.description(), request.hierarchyLevel(),
                false, true, companyId));
        audit.record("CREATE", "ROLE", role.getId(), Map.of("code", role.getCode()));
        return toDto(role);
    }

    @PutMapping("/{roleId}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('ROLE_UPDATE') || @permissionService.hasPermission('PLATFORM_ROLE_MANAGE')")
    public RbacDtos.RoleDto update(@PathVariable UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        if (role.isSystemRole() && !permissionService.isPlatformOwner()) {
            throw new BadRequestException("This role is protected and cannot be edited.");
        }
        if (!permissionService.isPlatformOwner()) {
            permissionService.requireCanManageRole(roleId);
        }
        if (request.hierarchyLevel() != null) {
            if (!permissionService.isPlatformOwner()
                    && request.hierarchyLevel() <= permissionService.getCurrentUserHierarchyLevel()) {
                throw new BadRequestException("You cannot manage a role with equal or higher hierarchy.");
            }
            role.setHierarchyLevel(request.hierarchyLevel());
        }
        if (request.name() != null && !request.name().isBlank()) {
            role.setName(request.name());
        }
        role.setDescription(request.description());
        audit.record("UPDATE", "ROLE", role.getId(), Map.of("code", role.getCode()));
        return toDto(roles.save(role));
    }

    @DeleteMapping("/{roleId}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('ROLE_DELETE') || @permissionService.hasPermission('PLATFORM_ROLE_MANAGE')")
    public void delete(@PathVariable UUID roleId) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        if (role.isSystemRole()) {
            throw new BadRequestException("System roles cannot be deleted");
        }
        if (!permissionService.isPlatformOwner()) {
            permissionService.requireCanManageRole(roleId);
        }
        if (userRoles.countByRoleId(roleId) > 0) {
            throw new BadRequestException("Cannot delete a role assigned to users.");
        }
        role.softDelete();
        roles.save(role);
        audit.record("DELETE", "ROLE", roleId, Map.of("code", role.getCode()));
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("@permissionService.hasAnyPermission('ROLE_VIEW','PERMISSION_VIEW','PLATFORM_ROLE_MANAGE')")
    public RoleWithPermissionsDto rolePermissions(@PathVariable UUID roleId) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        ensureVisible(role);
        return new RoleWithPermissionsDto(toDto(role), permissionsFor(roleId));
    }

    @PutMapping("/{roleId}/permissions")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('ROLE_ASSIGN_PERMISSION','PERMISSION_ASSIGN','PLATFORM_ROLE_MANAGE')")
    public RoleWithPermissionsDto replacePermissions(@PathVariable UUID roleId,
                                                     @Valid @RequestBody UpdateRolePermissionsRequest request) {
        Role role = roles.findByIdAndDeletedFalse(roleId)
                .orElseThrow(() -> new NotFoundException("Role", roleId));
        if (role.isSystemRole() && !permissionService.isPlatformOwner()) {
            throw new BadRequestException("This role is protected and cannot be edited.");
        }
        if (!permissionService.isPlatformOwner()) {
            permissionService.requireCanManageRole(roleId);
        }
        Set<UUID> requested = request.permissionIds() == null
                ? Set.of()
                : new LinkedHashSet<>(request.permissionIds());
        for (UUID permissionId : requested) {
            permissionService.requireCanAssignPermission(roleId, permissionId);
        }
        rolePermissions.deleteByRoleId(roleId);
        for (UUID permissionId : requested) {
            Permission permission = permissions.findByIdAndDeletedFalse(permissionId)
                    .orElseThrow(() -> new NotFoundException("Permission", permissionId));
            UUID companyId = role.getCompanyId();
            rolePermissions.save(new RolePermission(roleId, permission.getId(), companyId));
        }
        audit.record("UPDATE_PERMISSIONS", "ROLE", roleId,
                Map.of("permissionCount", requested.size(), "code", role.getCode()));
        return new RoleWithPermissionsDto(toDto(role), permissionsFor(roleId));
    }

    private RbacDtos.RoleDto toDto(Role role) {
        return mapper.toRoleDto(role, userRoles.countByRoleId(role.getId()),
                rolePermissions.findByRoleId(role.getId()).size());
    }

    private List<RbacDtos.PermissionDto> permissionsFor(UUID roleId) {
        return rolePermissions.findPermissionsForRoleIds(List.of(roleId)).stream()
                .map(mapper::toPermissionDto)
                .toList();
    }

    private void ensureVisible(Role role) {
        if (permissionService.isPlatformOwner()) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        if (role.getCompanyId() != null && !role.getCompanyId().equals(tenantId)) {
            throw new BadRequestException("Cross-company access denied.");
        }
    }

    private void ensureUniqueRoleCode(String code, UUID companyId, UUID existingId) {
        var existing = companyId == null
                ? roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(code)
                : roles.findByCodeAndCompanyIdAndDeletedFalse(code, companyId);
        existing.filter(role -> existingId == null || !role.getId().equals(existingId))
                .ifPresent(role -> {
                    throw new BadRequestException("Role code already exists in this scope.");
                });
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!RbacCatalog.isValidCode(normalized)) {
            throw new BadRequestException("Role code must be uppercase snake case.");
        }
        return normalized;
    }
}
