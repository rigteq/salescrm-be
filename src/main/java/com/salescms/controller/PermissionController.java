package com.salescms.controller;
import com.salescms.dto.RbacDtos;
import com.salescms.dto.RbacCatalog;
import com.salescms.service.PermissionService;
import com.salescms.repository.PermissionRepository;
import com.salescms.entity.Permission;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.mapper.SalesCmsMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    public record PermissionRequest(@NotBlank String code, @NotBlank String name,
                                    String description, @NotBlank String module,
                                    boolean systemPermission) {
    }

    private final PermissionRepository permissions;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final SalesCmsMapper mapper;

    public PermissionController(PermissionRepository permissions,
                                PermissionService permissionService,
                                AuditService audit, SalesCmsMapper mapper) {
        this.permissions = permissions;
        this.permissionService = permissionService;
        this.audit = audit;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('PERMISSION_VIEW','PLATFORM_PERMISSION_MANAGE')")
    public List<RbacDtos.PermissionDto> list() {
        return visiblePermissions().stream().map(mapper::toPermissionDto).toList();
    }

    @GetMapping("/modules")
    @PreAuthorize("@permissionService.hasAnyPermission('PERMISSION_VIEW','PLATFORM_PERMISSION_MANAGE')")
    public RbacDtos.PermissionModulesDto modules() {
        Map<String, List<RbacDtos.PermissionDto>> grouped = visiblePermissions().stream()
                .collect(Collectors.groupingBy(Permission::getModule, java.util.LinkedHashMap::new,
                        Collectors.mapping(mapper::toPermissionDto, Collectors.toList())));
        return new RbacDtos.PermissionModulesDto(grouped);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public RbacDtos.PermissionDto create(@Valid @RequestBody PermissionRequest request) {
        String code = normalizeCode(request.code());
        if (permissions.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BadRequestException("Permission code already exists");
        }
        Permission permission = permissions.save(new Permission(code, request.name(),
                request.description(), request.module(), request.systemPermission()));
        audit.record("CREATE", "PERMISSION", permission.getId(), Map.of("code", permission.getCode()));
        return mapper.toPermissionDto(permission);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public RbacDtos.PermissionDto update(@PathVariable UUID id, @Valid @RequestBody PermissionRequest request) {
        Permission permission = permissions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Permission", id));
        String code = normalizeCode(request.code());
        permissions.findByCodeAndDeletedFalse(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Permission code already exists");
                });
        permission.setCode(code);
        permission.setName(request.name());
        permission.setDescription(request.description());
        permission.setModule(request.module());
        permission.setSystemPermission(request.systemPermission());
        audit.record("UPDATE", "PERMISSION", permission.getId(), Map.of("code", permission.getCode()));
        return mapper.toPermissionDto(permissions.save(permission));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public void delete(@PathVariable UUID id) {
        Permission permission = permissions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Permission", id));
        if (permission.isSystemPermission()) {
            throw new BadRequestException("System permissions cannot be deleted");
        }
        permission.softDelete();
        permissions.save(permission);
        audit.record("DELETE", "PERMISSION", id, Map.of("code", permission.getCode()));
    }

    private List<Permission> visiblePermissions() {
        List<Permission> all = permissions.findByDeletedFalseOrderByModuleAscCodeAsc();
        if (permissionService.isPlatformOwner()) {
            return all;
        }
        return all.stream()
                .filter(permission -> !RbacCatalog.isPlatformPermission(permission.getCode()))
                .toList();
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!RbacCatalog.isValidCode(normalized)) {
            throw new BadRequestException("Permission code must be uppercase snake case.");
        }
        return normalized;
    }
}
