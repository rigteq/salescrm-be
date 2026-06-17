package com.salescms.platform.rbac;

import com.salescms.platform.auth.TenantRepository;
import com.salescms.platform.auth.User;
import com.salescms.platform.auth.UserRepository;
import com.salescms.mapping.SalesCmsMapper;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.salescms.platform.rbac.RbacDtos.PlatformDashboardDto;

@RestController
@RequestMapping("/api/platform")
public class PlatformAdminController {

    public record PlatformUserDto(UUID id, UUID companyId, String email, String firstName,
                                  String lastName, String role, String status) {
        static PlatformUserDto of(User user) {
            return new PlatformUserDto(user.getId(), user.getTenantId(), user.getEmail(),
                    user.getFirstName(), user.getLastName(), user.getRole(), user.getStatus());
        }
    }

    public record SystemHealthDto(String status, List<SystemHealthComponentDto> components, Instant checkedAt) {
    }

    public record SystemHealthComponentDto(String name, String status) {
    }

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRoleRepository userRoles;
    private final RolePermissionRepository rolePermissions;
    private final JdbcTemplate jdbc;
    private final SalesCmsMapper mapper;
    private final HealthEndpoint healthEndpoint;

    public PlatformAdminController(TenantRepository tenants, UserRepository users,
                                   RoleRepository roles, PermissionRepository permissions,
                                   UserRoleRepository userRoles, RolePermissionRepository rolePermissions,
                                   JdbcTemplate jdbc, SalesCmsMapper mapper, HealthEndpoint healthEndpoint) {
        this.tenants = tenants;
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_DASHBOARD_VIEW')")
    public PlatformDashboardDto dashboard() {
        return new PlatformDashboardDto(
                tenants.count(),
                tenants.countByStatus("ACTIVE"),
                users.countByDeletedFalse(),
                countTable("leads"),
                countTable("po_data"),
                "Not configured");
    }

    @GetMapping("/companies")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_COMPANY_MANAGE')")
    public List<RbacDtos.PlatformCompanyDto> companies() {
        return tenants.findAll().stream()
                .map(mapper::toPlatformCompanyDto)
                .toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_ROLE_MANAGE')")
    public List<RbacDtos.RoleDto> roles() {
        return roles.findByDeletedFalseOrderByHierarchyLevelAscNameAsc().stream()
                .map(role -> mapper.toRoleDto(role, userRoles.countByRoleId(role.getId()),
                        rolePermissions.findByRoleId(role.getId()).size()))
                .toList();
    }

    @GetMapping("/permissions")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public List<RbacDtos.PermissionDto> permissions() {
        return permissions.findByDeletedFalseOrderByModuleAscCodeAsc().stream()
                .map(mapper::toPermissionDto)
                .toList();
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_USER_MANAGE')")
    public List<PlatformUserDto> users() {
        return users.findAll().stream()
                .filter(user -> !user.isDeleted())
                .map(PlatformUserDto::of)
                .toList();
    }

    @GetMapping("/system-health")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_DASHBOARD_VIEW')")
    public SystemHealthDto systemHealth() {
        HealthComponent health = healthEndpoint.health();
        return new SystemHealthDto(status(health), healthComponents(health), Instant.now());
    }

    private long countTable(String table) {
        Boolean exists = jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, "public." + table);
        if (!Boolean.TRUE.equals(exists)) {
            return 0;
        }
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    private List<SystemHealthComponentDto> healthComponents(HealthComponent health) {
        if (health instanceof CompositeHealth composite) {
            return composite.getComponents().entrySet().stream()
                    .map(entry -> new SystemHealthComponentDto(entry.getKey(), status(entry.getValue())))
                    .toList();
        }
        return List.of(new SystemHealthComponentDto("application", status(health)));
    }

    private String status(HealthComponent health) {
        return health.getStatus().getCode();
    }
}
