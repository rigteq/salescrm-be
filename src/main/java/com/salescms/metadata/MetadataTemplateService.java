package com.salescms.metadata;

import com.salescms.platform.auth.Tenant;
import com.salescms.platform.auth.TenantRepository;
import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.rbac.Permission;
import com.salescms.platform.rbac.PermissionRepository;
import com.salescms.platform.rbac.RbacCatalog;
import com.salescms.platform.rbac.Role;
import com.salescms.platform.rbac.RolePermission;
import com.salescms.platform.rbac.RolePermissionRepository;
import com.salescms.platform.rbac.RoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MetadataTemplateService {

    private static final List<String> MODULE_ACTIONS =
            List.of("VIEW", "CREATE", "UPDATE", "DELETE", "ASSIGN", "EXPORT", "IMPORT", "APPROVE", "CONFIGURE");

    private final JdbcTemplate jdbc;
    private final TenantRepository tenants;
    private final IndustryTemplateRepository templates;
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;

    public MetadataTemplateService(JdbcTemplate jdbc, TenantRepository tenants,
                                   IndustryTemplateRepository templates,
                                   PermissionRepository permissions, RoleRepository roles,
                                   RolePermissionRepository rolePermissions) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.templates = templates;
        this.permissions = permissions;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
    }

    @Transactional
    public void seedTemplates() {
        seed(template("generic_sales", "Generic Sales CRM", "Pipeline-first CRM for B2B and service sales.", "SALES",
                List.of(
                        module("accounts", "Account", "Accounts", "building", 10),
                        module("contacts", "Contact", "Contacts", "user", 20),
                        module("leads", "Lead", "Leads", "funnel", 30),
                        module("opportunities", "Opportunity", "Opportunities", "target", 40),
                        module("quotes", "Quote", "Quotes", "doc", 50)),
                List.of(pipeline("opportunities", "sales", "Sales Pipeline", true, List.of(
                        stage("qualification", "Qualification", 10, "#3b82f6", 0, "OPEN", null),
                        stage("discovery", "Discovery", 25, "#6366f1", 1, "OPEN", null),
                        stage("proposal", "Proposal Sent", 50, "#f59e0b", 2, "OPEN", null),
                        stage("negotiation", "Negotiation", 75, "#ec4899", 3, "OPEN", null),
                        stage("won", "Closed Won", 100, "#10b981", 4, "CLOSED", "WON"),
                        stage("lost", "Closed Lost", 0, "#ef4444", 5, "CLOSED", "LOST")))),
                List.of(
                        field("contacts", "account", "Account", "LOOKUP", "{\"lookupModuleKey\":\"accounts\"}", false, 5),
                        field("opportunities", "account", "Account", "LOOKUP", "{\"lookupModuleKey\":\"accounts\"}", false, 5),
                        field("quotes", "opportunity", "Opportunity", "LOOKUP", "{\"lookupModuleKey\":\"opportunities\"}", false, 5),
                        field("contacts", "email", "Email", "EMAIL", "{}", false, 10),
                        field("contacts", "phone", "Phone", "PHONE", "{}", false, 20),
                        field("contacts", "title", "Title", "TEXT", "{}", false, 30),
                        field("leads", "email", "Email", "EMAIL", "{}", false, 30),
                        field("leads", "phone", "Phone", "PHONE", "{}", false, 40),
                        field("leads", "company_name", "Company", "TEXT", "{}", false, 50),
                        field("leads", "company_size", "Company size", "DROPDOWN", "{\"options\":[\"1-10\",\"11-50\",\"51-200\",\"201+\"]}", false, 60),
                        field("leads", "budget", "Budget", "CURRENCY", "{}", false, 70),
                        field("opportunities", "close_date", "Close date", "DATE", "{}", false, 6),
                        field("opportunities", "forecast_category", "Forecast category", "DROPDOWN", "{\"options\":[\"PIPELINE\",\"BEST_CASE\",\"COMMIT\",\"CLOSED\"]}", false, 7),
                        field("opportunities", "decision_date", "Decision date", "DATE", "{}", false, 10),
                        field("accounts", "industry", "Industry", "TEXT", "{}", false, 10),
                        field("accounts", "website", "Website", "URL", "{}", false, 20),
                        field("accounts", "phone", "Phone", "PHONE", "{}", false, 30),
                        field("accounts", "type", "Type", "TEXT", "{}", false, 40),
                        field("accounts", "billing_country", "Billing country", "TEXT", "{}", false, 50)),
                "Sales Dashboard"));

        seed(template("real_estate", "Real Estate CRM", "Property, buyer, seller, and deal tracking.", "REAL_ESTATE",
                List.of(
                        module("properties", "Property", "Properties", "building", 10),
                        module("buyers", "Buyer", "Buyers", "user", 20),
                        module("sellers", "Seller", "Sellers", "user", 30),
                        module("deals", "Deal", "Deals", "target", 40),
                        module("showings", "Showing", "Showings", "calendar", 50)),
                List.of(pipeline("deals", "real_estate_deals", "Real Estate Deal Pipeline", true, List.of(
                        stage("new_inquiry", "New Inquiry", 5, "#3b82f6", 0, "OPEN", null),
                        stage("qualified", "Qualified", 20, "#6366f1", 1, "OPEN", null),
                        stage("showing", "Showing Scheduled", 35, "#f59e0b", 2, "OPEN", null),
                        stage("offer", "Offer Made", 60, "#ec4899", 3, "OPEN", null),
                        stage("closing", "Closing", 85, "#14b8a6", 4, "OPEN", null),
                        stage("closed", "Closed", 100, "#10b981", 5, "CLOSED", "WON"),
                        stage("lost", "Lost", 0, "#ef4444", 6, "CLOSED", "LOST")))),
                List.of(
                        field("properties", "property_type", "Property type", "DROPDOWN", "{\"options\":[\"Apartment\",\"Villa\",\"Plot\",\"Commercial\"]}", true, 10),
                        field("properties", "location", "Location", "ADDRESS", "{}", true, 20),
                        field("properties", "bedrooms", "Bedrooms", "NUMBER", "{}", false, 30),
                        field("buyers", "preferred_location", "Preferred location", "ADDRESS", "{}", false, 10),
                        field("deals", "expected_close", "Expected close", "DATE", "{}", false, 10)),
                "Real Estate Dashboard"));

        seed(template("customer_care", "Customer Care CRM", "Cases, SLA, escalations, and support operations.", "CUSTOMER_CARE",
                List.of(
                        module("customers", "Customer", "Customers", "user", 10),
                        module("cases", "Case", "Cases", "doc", 20),
                        module("tickets", "Ticket", "Tickets", "check", 30),
                        module("knowledge", "Article", "Knowledge Base", "doc", 40)),
                List.of(pipeline("cases", "support_cases", "Support Case Pipeline", true, List.of(
                        stage("new", "New", 5, "#3b82f6", 0, "OPEN", null),
                        stage("triage", "Triage", 20, "#6366f1", 1, "OPEN", null),
                        stage("in_progress", "In Progress", 50, "#f59e0b", 2, "OPEN", null),
                        stage("waiting", "Waiting on Customer", 60, "#14b8a6", 3, "OPEN", null),
                        stage("resolved", "Resolved", 100, "#10b981", 4, "CLOSED", "WON"),
                        stage("closed", "Closed", 100, "#64748b", 5, "CLOSED", "WON")))),
                List.of(
                        field("cases", "sla_due_at", "SLA due at", "DATETIME", "{}", true, 10),
                        field("cases", "channel", "Channel", "DROPDOWN", "{\"options\":[\"Email\",\"Phone\",\"Chat\",\"WhatsApp\",\"Portal\"]}", false, 20),
                        field("cases", "severity", "Severity", "DROPDOWN", "{\"options\":[\"Low\",\"Medium\",\"High\",\"Critical\"]}", true, 30),
                        field("customers", "plan", "Plan", "TEXT", "{}", false, 10)),
                "Customer Care Dashboard"));
    }

    @Transactional
    public void cloneTemplateForTenant(UUID templateId, UUID tenantId, boolean completeSetup) {
        IndustryTemplate template = templates.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new NotFoundException("IndustryTemplate", templateId));
        cloneTemplateForTenant(template.getTemplateKey(), tenantId, completeSetup);
    }

    @Transactional
    public void cloneTemplateForTenant(String templateKey, UUID tenantId, boolean completeSetup) {
        IndustryTemplate template = templates.findByTemplateKey(templateKey)
                .orElseThrow(() -> new NotFoundException("IndustryTemplate", templateKey));
        UUID templateId = template.getId();

        jdbc.query("""
                select module_key, singular_label, plural_label, icon, display_order, enabled
                from industry_template_modules where template_id=? order by display_order
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            jdbc.update("""
                    insert into tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order, enabled)
                    values (?,?,?,?,?,?,?)
                    on conflict (tenant_id, module_key) do update
                    set singular_label=excluded.singular_label,
                        plural_label=excluded.plural_label,
                        icon=excluded.icon,
                        display_order=excluded.display_order,
                        enabled=excluded.enabled,
                        updated_at=now()
                    """, tenantId, rs.getString("module_key"), rs.getString("singular_label"),
                    rs.getString("plural_label"), rs.getString("icon"), rs.getInt("display_order"),
                    rs.getBoolean("enabled"));
            ensureModulePermissions(rs.getString("module_key"));
        }, templateId);

        jdbc.query("""
                select module_key, field_key, label, field_type, options_json, validation_json,
                       visibility_rules_json, required, display_order
                from industry_template_custom_fields where template_id=? order by module_key, display_order
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> jdbc.update("""
                insert into custom_fields (tenant_id, module_key, field_key, label, field_type, options_json,
                    validation_json, visibility_rules_json, required, display_order, system_field)
                values (?,?,?,?,?,?,?,?,?,?,true)
                on conflict (tenant_id, module_key, field_key) do update
                set label=excluded.label, field_type=excluded.field_type, options_json=excluded.options_json,
                    validation_json=excluded.validation_json, visibility_rules_json=excluded.visibility_rules_json,
                    required=excluded.required, display_order=excluded.display_order, updated_at=now()
                """, tenantId, rs.getString("module_key"), rs.getString("field_key"), rs.getString("label"),
                rs.getString("field_type"), rs.getString("options_json"), rs.getString("validation_json"),
                rs.getString("visibility_rules_json"), rs.getBoolean("required"), rs.getInt("display_order")), templateId);

        jdbc.query("""
                select id, module_key, name, is_default from industry_template_pipelines
                where template_id=? order by module_key, name
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String moduleKey = rs.getString("module_key");
            Long existing = jdbc.queryForObject(
                    "select count(*) from pipelines where tenant_id=? and module_key=? and soft_deleted_at is null",
                    Long.class, tenantId, moduleKey);
            if (existing != null && existing > 0) {
                return;
            }
            UUID pipelineId = jdbc.queryForObject("""
                    insert into pipelines (tenant_id, module_key, name, is_default)
                    values (?,?,?,?)
                    returning id
                    """, UUID.class, tenantId, moduleKey, rs.getString("name"), rs.getBoolean("is_default"));
            jdbc.query("""
                    select stage_key, name, probability, color, sequence, stage_status, outcome_type
                    from industry_template_stages where template_pipeline_id=? order by sequence
                    """, (org.springframework.jdbc.core.RowCallbackHandler) stage -> jdbc.update("""
                    insert into stages (tenant_id, pipeline_id, name, position, probability, is_won, is_lost,
                        color, sequence, stage_status, outcome_type)
                    values (?,?,?,?,?,?,?,?,?,?,?)
                    """, tenantId, pipelineId, stage.getString("name"), stage.getInt("sequence"),
                    stage.getInt("probability"), "WON".equals(stage.getString("outcome_type")),
                    "LOST".equals(stage.getString("outcome_type")), stage.getString("color"),
                    stage.getInt("sequence"), stage.getString("stage_status"), stage.getString("outcome_type")),
                    rs.getObject("id", UUID.class));
        }, templateId);

        jdbc.query("""
                select module_key, form_key, name, field_keys_json
                from industry_template_forms where template_id=?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            Long existing = jdbc.queryForObject(
                    "select count(*) from forms where tenant_id=? and module_key=? and name=? and soft_deleted_at is null",
                    Long.class, tenantId, rs.getString("module_key"), rs.getString("name"));
            if (existing == null || existing == 0) {
                jdbc.update("insert into forms (tenant_id, module_key, name, description) values (?,?,?,?)",
                        tenantId, rs.getString("module_key"), rs.getString("name"), "Seeded from " + template.getName());
            }
        }, templateId);

        Long dashboards = jdbc.queryForObject(
                "select count(*) from dashboards where tenant_id=? and soft_deleted_at is null", Long.class, tenantId);
        if (dashboards == null || dashboards == 0) {
            UUID dashboardId = jdbc.queryForObject("""
                    insert into dashboards (tenant_id, name, description, is_default)
                    values (?,?,?,true) returning id
                    """, UUID.class, tenantId, template.getName() + " Dashboard", "Seeded dashboard");
            jdbc.update("""
                    insert into dashboard_widgets (tenant_id, dashboard_id, module_key, title, widget_type, metric_key, display_order)
                    values (?,?,?,?,?,?,?)
                    """, tenantId, dashboardId, null, "Open records", "COUNT", "open_count", 0);
        }

        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        tenant.markTemplateSelected(template.getTemplateKey());
        if (completeSetup) {
            tenant.completeSetup();
        }
        tenants.save(tenant);
    }

    private void seed(TemplateDef def) {
        UUID templateId = jdbc.queryForObject("""
                insert into industry_templates (template_key, name, description, business_type, active)
                values (?,?,?,?,true)
                on conflict (template_key) do update
                set name=excluded.name, description=excluded.description,
                    business_type=excluded.business_type, active=true, updated_at=now()
                returning id
                """, UUID.class, def.key(), def.name(), def.description(), def.businessType());
        jdbc.update("delete from industry_template_roles where template_id=?", templateId);
        jdbc.update("delete from industry_template_dashboards where template_id=?", templateId);
        jdbc.update("delete from industry_template_workflows where template_id=?", templateId);
        jdbc.update("delete from industry_template_forms where template_id=?", templateId);
        jdbc.update("delete from industry_template_custom_fields where template_id=?", templateId);
        jdbc.update("delete from industry_template_stages where template_pipeline_id in (select id from industry_template_pipelines where template_id=?)", templateId);
        jdbc.update("delete from industry_template_pipelines where template_id=?", templateId);
        jdbc.update("delete from industry_template_modules where template_id=?", templateId);

        for (ModuleSpec module : def.modules()) {
            jdbc.update("""
                    insert into industry_template_modules
                    (template_id, module_key, singular_label, plural_label, icon, display_order, enabled)
                    values (?,?,?,?,?,?,true)
                    """, templateId, module.key(), module.singular(), module.plural(), module.icon(), module.order());
        }
        for (PipelineSpec pipeline : def.pipelines()) {
            UUID pipelineId = jdbc.queryForObject("""
                    insert into industry_template_pipelines (template_id, module_key, pipeline_key, name, is_default)
                    values (?,?,?,?,?) returning id
                    """, UUID.class, templateId, pipeline.moduleKey(), pipeline.key(), pipeline.name(), pipeline.isDefault());
            for (StageSpec stage : pipeline.stages()) {
                jdbc.update("""
                        insert into industry_template_stages
                        (template_pipeline_id, stage_key, name, probability, color, sequence, stage_status, outcome_type)
                        values (?,?,?,?,?,?,?,?)
                        """, pipelineId, stage.key(), stage.name(), stage.probability(), stage.color(),
                        stage.sequence(), stage.stageStatus(), stage.outcomeType());
            }
        }
        for (FieldSpec field : def.fields()) {
            jdbc.update("""
                    insert into industry_template_custom_fields
                    (template_id, module_key, field_key, label, field_type, options_json, required, display_order)
                    values (?,?,?,?,?,?,?,?)
                    """, templateId, field.moduleKey(), field.key(), field.label(), field.type(),
                    field.optionsJson(), field.required(), field.order());
        }
        for (ModuleSpec module : def.modules()) {
            jdbc.update("""
                    insert into industry_template_forms (template_id, module_key, form_key, name, field_keys_json)
                    values (?,?,?,?,?)
                    """, templateId, module.key(), "default_" + module.key(), module.singular() + " Form", "[]");
        }
        jdbc.update("""
                insert into industry_template_dashboards (template_id, dashboard_key, name, widgets_json)
                values (?,?,?,?)
                """, templateId, "default", def.dashboardName(), "[]");
        jdbc.update("""
                insert into industry_template_workflows (template_id, module_key, name, trigger_type, conditions_json, actions_json)
                values (?,?,?,?,?,?)
                """, templateId, def.modules().get(0).key(), "Notify on new " + def.modules().get(0).singular(),
                "ON_CREATE", "{}", "[{\"type\":\"send_notification\"}]");
    }

    public void ensureModulePermissions(String moduleKey) {
        for (String action : MODULE_ACTIONS) {
            String code = "MODULE_" + moduleKey.toUpperCase().replaceAll("[^A-Z0-9]+", "_") + "_" + action;
            Permission permission = permissions.findByCodeAndDeletedFalse(code)
                    .orElseGet(() -> permissions.save(new Permission(code, action + " " + moduleKey,
                            "Dynamic module permission for " + moduleKey + " " + action.toLowerCase(),
                            "Dynamic Modules", false)));
            assignPermissionToRole(permission, RbacCatalog.COMPANY_OWNER);
            assignPermissionToRole(permission, RbacCatalog.COMPANY_ADMIN);
        }
    }

    private void assignPermissionToRole(Permission permission, String roleCode) {
        Role role = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        boolean exists = rolePermissions.findByRoleId(role.getId()).stream()
                .anyMatch(rp -> rp.getPermissionId().equals(permission.getId()));
        if (!exists) {
            rolePermissions.save(new RolePermission(role.getId(), permission.getId(), null));
        }
    }

    private record TemplateDef(String key, String name, String description, String businessType,
                               List<ModuleSpec> modules, List<PipelineSpec> pipelines,
                               List<FieldSpec> fields, String dashboardName) {
    }

    private record ModuleSpec(String key, String singular, String plural, String icon, int order) {
    }

    private record PipelineSpec(String moduleKey, String key, String name, boolean isDefault,
                                List<StageSpec> stages) {
    }

    private record StageSpec(String key, String name, int probability, String color, int sequence,
                             String stageStatus, String outcomeType) {
    }

    private record FieldSpec(String moduleKey, String key, String label, String type,
                             String optionsJson, boolean required, int order) {
    }

    private TemplateDef template(String key, String name, String description, String businessType,
                                 List<ModuleSpec> modules, List<PipelineSpec> pipelines,
                                 List<FieldSpec> fields, String dashboardName) {
        return new TemplateDef(key, name, description, businessType, modules, pipelines, fields, dashboardName);
    }

    private ModuleSpec module(String key, String singular, String plural, String icon, int order) {
        return new ModuleSpec(key, singular, plural, icon, order);
    }

    private PipelineSpec pipeline(String moduleKey, String key, String name, boolean isDefault, List<StageSpec> stages) {
        return new PipelineSpec(moduleKey, key, name, isDefault, stages);
    }

    private StageSpec stage(String key, String name, int probability, String color, int sequence,
                            String stageStatus, String outcomeType) {
        return new StageSpec(key, name, probability, color, sequence, stageStatus, outcomeType);
    }

    private FieldSpec field(String moduleKey, String key, String label, String type,
                            String optionsJson, boolean required, int order) {
        return new FieldSpec(moduleKey, key, label, type, optionsJson, required, order);
    }
}
