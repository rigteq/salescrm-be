package com.salescms.util;
import com.salescms.repository.UserRoleRepository;
import com.salescms.entity.UserRole;
import com.salescms.repository.RoleRepository;
import com.salescms.repository.RolePermissionRepository;
import com.salescms.entity.RolePermission;
import com.salescms.entity.Role;
import com.salescms.dto.RbacCatalog;
import com.salescms.repository.PermissionRepository;
import com.salescms.entity.Permission;

import com.salescms.entity.Tenant;
import com.salescms.repository.TenantRepository;
import com.salescms.entity.User;
import com.salescms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(0)
public class RbacSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacSeeder.class);

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final UserRoleRepository userRoles;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final String platformOwnerEmail;
    private final String platformOwnerPassword;

    public RbacSeeder(PermissionRepository permissions, RoleRepository roles,
                      RolePermissionRepository rolePermissions, UserRoleRepository userRoles,
                      UserRepository users, TenantRepository tenants, PasswordEncoder passwordEncoder,
                      @Value("${salescms.platform-owner.email:platform-owner@salescms.local}") String platformOwnerEmail,
                      @Value("${salescms.platform-owner.password:}") String platformOwnerPassword) {
        this.permissions = permissions;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.userRoles = userRoles;
        this.users = users;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.platformOwnerEmail = platformOwnerEmail.toLowerCase();
        this.platformOwnerPassword = platformOwnerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedRoles();
        seedRolePermissions();
        assignExistingUsers();
        seedPlatformOwner();
    }

    private void seedPermissions() {
        for (var seed : RbacCatalog.permissions()) {
            Permission permission = permissions.findByCodeAndDeletedFalse(seed.code())
                    .orElseGet(() -> new Permission(seed.code(), seed.name(), seed.description(), seed.module(), true));
            permission.setName(seed.name());
            permission.setDescription(seed.description());
            permission.setModule(seed.module());
            permission.setSystemPermission(true);
            permissions.save(permission);
        }
    }

    private void seedRoles() {
        for (var seed : RbacCatalog.roles()) {
            Role role = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(seed.code())
                    .orElseGet(() -> new Role(seed.code(), seed.name(), seed.description(),
                            seed.hierarchyLevel(), true, seed.editable(), null));
            role.setName(seed.name());
            role.setDescription(seed.description());
            role.setHierarchyLevel(seed.hierarchyLevel());
            role.setSystemRole(true);
            role.setEditable(seed.editable());
            roles.save(role);
        }
    }

    private void seedRolePermissions() {
        Map<String, Permission> byCode = permissions.findByDeletedFalseOrderByModuleAscCodeAsc().stream()
                .collect(Collectors.toMap(Permission::getCode, p -> p, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, java.util.Set<UUID>> existing = rolePermissions.findAll().stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        Collectors.mapping(RolePermission::getPermissionId, Collectors.toSet())));

        RbacCatalog.defaultRolePermissions().forEach((roleCode, permissionCodes) -> {
            Role role = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(roleCode).orElseThrow();
            java.util.Set<UUID> existingForRole = existing.getOrDefault(role.getId(), java.util.Set.of());
            for (String code : permissionCodes) {
                Permission permission = byCode.get(code);
                if (permission != null && !existingForRole.contains(permission.getId())) {
                    rolePermissions.save(new RolePermission(role.getId(), permission.getId(), null));
                }
            }
        });
    }

    private void assignExistingUsers() {
        for (User user : users.findAll()) {
            if (user.isDeleted()) {
                continue;
            }
            if (!userRoles.findByUserIdAndCompanyId(user.getId(), user.getTenantId()).isEmpty()) {
                continue;
            }
            String roleCode = legacyRole(user.getRole());
            Role role = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(roleCode)
                    .orElseGet(() -> roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(RbacCatalog.SALES_EXECUTIVE)
                            .orElseThrow());
            user.setRole(role.getCode());
            users.save(user);
            userRoles.save(new UserRole(user.getId(), role.getId(), user.getTenantId(), true));
        }
    }

    private void seedPlatformOwner() {
        if (platformOwnerPassword == null || platformOwnerPassword.isBlank()) {
            log.info("RBAC platform owner bootstrap skipped; set SALESCMS_PLATFORM_OWNER_PASSWORD to create one.");
            return;
        }
        Tenant platformTenant = tenants.findBySlug("platform")
                .orElseGet(() -> tenants.save(new Tenant("SalesCMS Platform", "platform")));
        Role platformRole = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(RbacCatalog.PLATFORM_OWNER)
                .orElseThrow();
        User owner = users.findByEmail(platformOwnerEmail)
                .orElseGet(() -> users.save(new User(platformTenant.getId(), platformOwnerEmail,
                        passwordEncoder.encode(platformOwnerPassword), "Platform", "Owner",
                        RbacCatalog.PLATFORM_OWNER)));
        owner.setRole(RbacCatalog.PLATFORM_OWNER);
        owner.setStatus("ACTIVE");
        users.save(owner);
        if (userRoles.findByUserIdAndRoleIdAndCompanyId(owner.getId(), platformRole.getId(), platformTenant.getId())
                .isEmpty()) {
            userRoles.save(new UserRole(owner.getId(), platformRole.getId(), platformTenant.getId(), true));
        }
        log.info("RBAC platform owner bootstrap ensured for configured email.");
    }

    private String legacyRole(String role) {
        if ("ADMIN".equals(role)) {
            return RbacCatalog.COMPANY_OWNER;
        }
        if ("MANAGER".equals(role)) {
            return RbacCatalog.SALES_MANAGER;
        }
        if ("REP".equals(role)) {
            return RbacCatalog.SALES_EXECUTIVE;
        }
        return role != null ? role : RbacCatalog.SALES_EXECUTIVE;
    }
}
