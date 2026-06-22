package com.salescms.platform.rbac;

import com.salescms.dto.RbacCatalog.PermissionSeed;
import com.salescms.dto.RbacCatalog.RoleSeed;
import com.salescms.dto.RbacCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-function guardrails for the RBAC catalog. These lock down the role
 * hierarchy and the invariant that drives method security: platform-wide
 * permissions must never be seeded onto tenant roles, and the Viewer role must
 * stay read-only. A regression here would silently widen what a role can do.
 */
class RbacCatalogTest {

    private static final Set<String> PLATFORM_ROLES =
            Set.of(RbacCatalog.PLATFORM_OWNER, RbacCatalog.PLATFORM_MANAGER);

    private Set<String> allPermissionCodes() {
        return RbacCatalog.permissions().stream()
                .map(PermissionSeed::code)
                .collect(Collectors.toSet());
    }

    @Test
    void definesEightRolesWithAUniqueOrderedHierarchy() {
        List<RoleSeed> roles = RbacCatalog.roles();
        assertThat(roles).hasSize(8);

        // Codes are unique.
        assertThat(roles).extracting(RoleSeed::code).doesNotHaveDuplicates();

        // Hierarchy levels are unique and span 1..8 (1 = highest authority).
        assertThat(roles).extracting(RoleSeed::hierarchyLevel)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);

        // Platform owner sits at the top; viewer at the bottom.
        Map<String, Integer> levelByCode = roles.stream()
                .collect(Collectors.toMap(RoleSeed::code, RoleSeed::hierarchyLevel));
        assertThat(levelByCode.get(RbacCatalog.PLATFORM_OWNER)).isEqualTo(1);
        assertThat(levelByCode.get(RbacCatalog.VIEWER)).isEqualTo(8);
        // A company owner must outrank a sales manager.
        assertThat(levelByCode.get(RbacCatalog.COMPANY_OWNER))
                .isLessThan(levelByCode.get(RbacCatalog.SALES_MANAGER));
    }

    @Test
    void permissionCodesAreUniqueAndWellFormed() {
        List<PermissionSeed> perms = RbacCatalog.permissions();
        assertThat(perms).extracting(PermissionSeed::code).doesNotHaveDuplicates();
        assertThat(perms).allSatisfy(p -> {
            assertThat(RbacCatalog.isValidCode(p.code())).as("valid code: %s", p.code()).isTrue();
            assertThat(p.module()).isNotBlank();
        });
    }

    @Test
    void isPlatformPermissionMatchesThePlatformPrefix() {
        assertThat(RbacCatalog.isPlatformPermission("PLATFORM_DASHBOARD_VIEW")).isTrue();
        assertThat(RbacCatalog.isPlatformPermission("LEAD_VIEW_ALL")).isFalse();
        assertThat(RbacCatalog.isPlatformPermission(null)).isFalse();
    }

    @Test
    void everyRoleHasDefaultsAndGrantsOnlyRealPermissions() {
        Map<String, Set<String>> defaults = RbacCatalog.defaultRolePermissions();
        Set<String> known = allPermissionCodes();

        // Every defined role gets a defaults entry...
        assertThat(defaults.keySet())
                .containsAll(RbacCatalog.roles().stream().map(RoleSeed::code).toList());

        // ...and no entry grants a permission that doesn't exist.
        defaults.forEach((role, granted) ->
                assertThat(known).as("role %s grants only known permissions", role).containsAll(granted));
    }

    @Test
    void platformOwnerHoldsEveryPermission() {
        assertThat(RbacCatalog.defaultRolePermissions().get(RbacCatalog.PLATFORM_OWNER))
                .containsExactlyInAnyOrderElementsOf(allPermissionCodes());
    }

    @Test
    void onlyPlatformRolesMayHoldPlatformPermissions() {
        RbacCatalog.defaultRolePermissions().forEach((role, granted) -> {
            boolean grantsPlatform = granted.stream().anyMatch(RbacCatalog::isPlatformPermission);
            if (!PLATFORM_ROLES.contains(role)) {
                assertThat(grantsPlatform)
                        .as("tenant role %s must not hold any PLATFORM_* permission", role)
                        .isFalse();
            }
        });
    }

    @Test
    void viewerIsReadOnly() {
        Set<String> viewer = RbacCatalog.defaultRolePermissions().get(RbacCatalog.VIEWER);
        assertThat(viewer).isNotEmpty();
        assertThat(viewer).noneSatisfy(code ->
                assertThat(code).matches(".*_(CREATE|UPDATE|DELETE|ASSIGN|IMPORT|SEND|MANAGE)$"));
    }
}

