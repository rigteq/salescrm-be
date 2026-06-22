package com.salescms.dto;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RbacCatalog {

    public static final String PLATFORM_OWNER = "PLATFORM_OWNER";
    public static final String PLATFORM_MANAGER = "PLATFORM_MANAGER";
    public static final String COMPANY_OWNER = "COMPANY_OWNER";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
    public static final String SALES_MANAGER = "SALES_MANAGER";
    public static final String SALES_EXECUTIVE = "SALES_EXECUTIVE";
    public static final String FINANCE = "FINANCE";
    public static final String VIEWER = "VIEWER";

    private RbacCatalog() {
    }

    public record PermissionSeed(String code, String name, String description, String module) {
    }

    public record RoleSeed(String code, String name, String description, int hierarchyLevel,
                           boolean editable) {
    }

    public static List<RoleSeed> roles() {
        return List.of(
                new RoleSeed(PLATFORM_OWNER, "Platform Owner",
                        "SaaS owner with global platform access.", 1, false),
                new RoleSeed(PLATFORM_MANAGER, "Platform Manager",
                        "Platform operator managing tenants, users, and support.", 2, false),
                new RoleSeed(COMPANY_OWNER, "Company Owner",
                        "Tenant owner with full company administration.", 3, false),
                new RoleSeed(COMPANY_ADMIN, "Company Admin",
                        "Company administrator for users, CRM data, and settings.", 4, false),
                new RoleSeed(SALES_MANAGER, "Sales Manager",
                        "Sales team lead with team pipeline access.", 5, false),
                new RoleSeed(SALES_EXECUTIVE, "Sales Executive",
                        "Sales seller with assigned-record access.", 6, false),
                new RoleSeed(FINANCE, "Finance",
                        "Finance operator for purchase orders and reports.", 7, false),
                new RoleSeed(VIEWER, "Viewer",
                        "Read-only business user.", 8, false));
    }

    public static List<PermissionSeed> permissions() {
        return List.of(
                permission("COMPANY_VIEW", "View companies", "View company records.", "Company"),
                permission("COMPANY_CREATE", "Create companies", "Create tenant companies.", "Company"),
                permission("COMPANY_UPDATE", "Update companies", "Update company records.", "Company"),
                permission("COMPANY_DELETE", "Delete companies", "Deactivate or delete companies.", "Company"),
                permission("COMPANY_MANAGE_SETTINGS", "Manage company settings", "Manage tenant-level settings.", "Company"),

                permission("USER_VIEW", "View users", "View company users.", "Users"),
                permission("USER_CREATE", "Create users", "Create users.", "Users"),
                permission("USER_UPDATE", "Update users", "Update users.", "Users"),
                permission("USER_DELETE", "Delete users", "Delete or deactivate users.", "Users"),
                permission("USER_ACTIVATE", "Activate users", "Activate users.", "Users"),
                permission("USER_DEACTIVATE", "Deactivate users", "Deactivate users.", "Users"),
                permission("USER_ASSIGN_ROLE", "Assign roles", "Assign and remove user roles.", "Users"),

                permission("ROLE_VIEW", "View roles", "View roles.", "Roles"),
                permission("ROLE_CREATE", "Create roles", "Create custom company roles.", "Roles"),
                permission("ROLE_UPDATE", "Update roles", "Update custom roles.", "Roles"),
                permission("ROLE_DELETE", "Delete roles", "Delete custom roles.", "Roles"),
                permission("ROLE_ASSIGN_PERMISSION", "Manage role permissions", "Assign permissions to roles.", "Roles"),
                permission("PERMISSION_VIEW", "View permissions", "View permissions.", "Roles"),
                permission("PERMISSION_ASSIGN", "Assign permissions", "Assign permissions.", "Roles"),
                permission("PERMISSION_REVOKE", "Revoke permissions", "Revoke permissions.", "Roles"),

                permission("LEAD_VIEW_ALL", "View all leads", "View all company leads.", "Leads"),
                permission("LEAD_VIEW_TEAM", "View team leads", "View team leads.", "Leads"),
                permission("LEAD_VIEW_ASSIGNED", "View assigned leads", "View assigned leads.", "Leads"),
                permission("LEAD_CREATE", "Create leads", "Create leads.", "Leads"),
                permission("LEAD_UPDATE", "Update leads", "Update leads.", "Leads"),
                permission("LEAD_DELETE", "Delete leads", "Delete leads.", "Leads"),
                permission("LEAD_ASSIGN", "Assign leads", "Assign leads.", "Leads"),
                permission("LEAD_IMPORT", "Import leads", "Import lead CSV files.", "Leads"),
                permission("LEAD_EXPORT", "Export leads", "Export lead CSV files.", "Leads"),

                permission("COMMENT_VIEW", "View comments", "View record comments.", "Comments"),
                permission("COMMENT_CREATE", "Create comments", "Create record comments.", "Comments"),
                permission("COMMENT_DELETE", "Delete comments", "Delete record comments.", "Comments"),

                permission("PO_VIEW_ALL", "View all purchase orders", "View all company POs.", "POs"),
                permission("PO_VIEW_OWN", "View own purchase orders", "View own POs.", "POs"),
                permission("PO_CREATE", "Create purchase orders", "Create POs.", "POs"),
                permission("PO_UPDATE", "Update purchase orders", "Update POs.", "POs"),
                permission("PO_DELETE", "Delete purchase orders", "Delete POs.", "POs"),
                permission("PO_EXPORT", "Export purchase orders", "Export POs.", "POs"),

                permission("NOTIFICATION_VIEW", "View notifications", "View notifications.", "Notifications"),
                permission("NOTIFICATION_SEND", "Send notifications", "Send notifications.", "Notifications"),
                permission("NOTIFICATION_DELETE", "Delete notifications", "Delete notifications.", "Notifications"),

                permission("REPORT_VIEW_COMPANY", "View company reports", "View company-level reports.", "Reports"),
                permission("REPORT_VIEW_TEAM", "View team reports", "View team reports.", "Reports"),
                permission("REPORT_VIEW_OWN", "View own reports", "View own reports.", "Reports"),
                permission("REPORT_EXPORT", "Export reports", "Export reports.", "Reports"),

                permission("SETTINGS_VIEW", "View settings", "View tenant settings.", "Settings"),
                permission("SETTINGS_UPDATE", "Update settings", "Update tenant settings.", "Settings"),
                permission("WHATSAPP_TEMPLATE_MANAGE", "Manage WhatsApp templates", "Manage WhatsApp message templates.", "Settings"),
                permission("CUSTOM_FIELD_MANAGE", "Manage custom fields", "Manage custom fields.", "Settings"),

                permission("BILLING_VIEW", "View billing", "View billing data.", "Billing"),
                permission("BILLING_MANAGE", "Manage billing", "Manage billing data.", "Billing"),

                permission("AUDIT_LOG_VIEW", "View audit log", "View audit logs.", "Audit"),

                permission("PLATFORM_DASHBOARD_VIEW", "View platform dashboard", "View platform owner dashboard.", "Platform"),
                permission("PLATFORM_COMPANY_MANAGE", "Manage platform companies", "Manage tenants across the platform.", "Platform"),
                permission("PLATFORM_USER_MANAGE", "Manage platform users", "Manage users across tenants.", "Platform"),
                permission("PLATFORM_ROLE_MANAGE", "Manage platform roles", "Manage global and company roles.", "Platform"),
                permission("PLATFORM_PERMISSION_MANAGE", "Manage platform permissions", "Manage platform permissions.", "Platform"),
                permission("PLATFORM_BILLING_MANAGE", "Manage platform billing", "Manage billing across tenants.", "Platform"),
                permission("PLATFORM_FEATURE_FLAG_MANAGE", "Manage feature flags", "Manage platform feature flags and tenant overrides.", "Platform"),
                permission("PLATFORM_AUDIT_VIEW", "View platform audit", "View platform-level audit data.", "Platform"),
                permission("PLATFORM_SUPPORT_ACCESS", "Platform support access", "Access tenants for support.", "Platform"));
    }

    public static Map<String, Set<String>> defaultRolePermissions() {
        LinkedHashMap<String, Set<String>> map = new LinkedHashMap<>();
        Set<String> all = codes(permissions());
        Set<String> nonPlatform = new LinkedHashSet<>(all);
        nonPlatform.removeIf(code -> code.startsWith("PLATFORM_"));

        map.put(PLATFORM_OWNER, all);
        map.put(PLATFORM_MANAGER, set(
                "PLATFORM_DASHBOARD_VIEW", "PLATFORM_COMPANY_MANAGE", "PLATFORM_USER_MANAGE",
                "PLATFORM_SUPPORT_ACCESS", "PLATFORM_AUDIT_VIEW",
                "COMPANY_VIEW", "USER_VIEW", "REPORT_VIEW_COMPANY", "AUDIT_LOG_VIEW"));
        map.put(COMPANY_OWNER, nonPlatform);
        map.put(COMPANY_ADMIN, set(
                "USER_VIEW", "USER_CREATE", "USER_UPDATE", "USER_DELETE", "USER_ACTIVATE", "USER_DEACTIVATE",
                "USER_ASSIGN_ROLE", "ROLE_VIEW", "PERMISSION_VIEW", "PERMISSION_ASSIGN", "PERMISSION_REVOKE",
                "LEAD_VIEW_ALL", "LEAD_CREATE", "LEAD_UPDATE", "LEAD_DELETE", "LEAD_ASSIGN", "LEAD_IMPORT",
                "LEAD_EXPORT", "COMMENT_VIEW", "COMMENT_CREATE", "COMMENT_DELETE", "PO_VIEW_ALL", "PO_CREATE",
                "PO_UPDATE", "PO_DELETE", "REPORT_VIEW_COMPANY", "REPORT_EXPORT", "NOTIFICATION_VIEW",
                "NOTIFICATION_SEND", "SETTINGS_VIEW", "SETTINGS_UPDATE", "WHATSAPP_TEMPLATE_MANAGE"));
        map.put(SALES_MANAGER, set(
                "USER_VIEW", "LEAD_VIEW_TEAM", "LEAD_VIEW_ASSIGNED", "LEAD_CREATE", "LEAD_UPDATE", "LEAD_ASSIGN",
                "COMMENT_VIEW", "COMMENT_CREATE", "PO_VIEW_OWN", "PO_CREATE", "REPORT_VIEW_TEAM",
                "NOTIFICATION_VIEW"));
        map.put(SALES_EXECUTIVE, set(
                "LEAD_VIEW_ASSIGNED", "LEAD_CREATE", "LEAD_UPDATE", "COMMENT_VIEW", "COMMENT_CREATE",
                "PO_VIEW_OWN", "PO_CREATE", "REPORT_VIEW_OWN", "NOTIFICATION_VIEW"));
        map.put(FINANCE, set(
                "LEAD_VIEW_ALL", "PO_VIEW_ALL", "PO_CREATE", "PO_UPDATE", "PO_EXPORT", "REPORT_VIEW_COMPANY",
                "NOTIFICATION_VIEW"));
        map.put(VIEWER, set(
                "LEAD_VIEW_ASSIGNED", "COMMENT_VIEW", "PO_VIEW_OWN", "REPORT_VIEW_OWN", "NOTIFICATION_VIEW"));
        return map;
    }

    public static boolean isPlatformPermission(String code) {
        return code != null && code.startsWith("PLATFORM_");
    }

    public static boolean isValidCode(String code) {
        return code != null && code.matches("^[A-Z0-9_]+$");
    }

    private static PermissionSeed permission(String code, String name, String description, String module) {
        return new PermissionSeed(code, name, description, module);
    }

    private static Set<String> set(String... codes) {
        return new LinkedHashSet<>(List.of(codes));
    }

    private static Set<String> codes(List<PermissionSeed> permissions) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        permissions.forEach(permission -> codes.add(permission.code()));
        return codes;
    }
}
