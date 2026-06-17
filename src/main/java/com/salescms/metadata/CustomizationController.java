package com.salescms.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.platform.common.BadRequestException;
import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.rbac.PermissionService;
import com.salescms.platform.tenancy.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.salescms.metadata.MetadataDtos.AssignmentRuleDto;
import static com.salescms.metadata.MetadataDtos.AssignmentRuleRequest;
import static com.salescms.metadata.MetadataDtos.DashboardWidgetDto;
import static com.salescms.metadata.MetadataDtos.DashboardWidgetRequest;
import static com.salescms.metadata.MetadataDtos.FormDto;
import static com.salescms.metadata.MetadataDtos.FormRequest;
import static com.salescms.metadata.MetadataDtos.RecordDto;
import static com.salescms.metadata.MetadataDtos.TeamDto;
import static com.salescms.metadata.MetadataDtos.TeamMemberDto;
import static com.salescms.metadata.MetadataDtos.TeamMemberRequest;
import static com.salescms.metadata.MetadataDtos.TeamRequest;
import static com.salescms.metadata.MetadataDtos.WorkflowDto;
import static com.salescms.metadata.MetadataDtos.WorkflowRequest;

@RestController
public class CustomizationController {

    private static final Set<String> TRIGGERS = Set.of("ON_CREATE", "ON_UPDATE", "ON_STAGE_CHANGE",
            "ON_ASSIGNMENT_CHANGE", "ON_NO_ACTIVITY", "ON_DATE_REACHED", "ON_STATUS_CHANGE");
    private static final Set<String> ASSIGNMENT_TYPES = Set.of("MANUAL", "ROUND_ROBIN", "TEAM_BASED",
            "LOCATION_BASED", "PRIORITY_BASED", "PRODUCT_BASED", "WORKLOAD_BASED");
    private static final Set<String> WIDGET_TYPES = Set.of("COUNT", "SUM", "FUNNEL", "BAR_CHART",
            "LINE_CHART", "PIE_CHART", "TABLE", "LEADERBOARD", "SLA_CARD", "TASK_LIST");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DynamicRecordService records;
    private final PermissionService permissions;

    public CustomizationController(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                   DynamicRecordService records, PermissionService permissions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.records = records;
        this.permissions = permissions;
    }

    @GetMapping("/api/forms")
    @PreAuthorize("@permissionService.hasAnyPermission('SETTINGS_VIEW','LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public List<FormDto> forms(@RequestParam(required = false) String moduleKey) {
        UUID tenantId = TenantContext.requireTenantId();
        if (moduleKey == null || moduleKey.isBlank()) {
            return jdbc.query("""
                    select id, module_key, name, description, pipeline_id, default_stage_id, active
                    from forms
                    where tenant_id=? and soft_deleted_at is null
                    order by module_key, name
                    """, this::formDto, tenantId);
        }
        return jdbc.query("""
                select id, module_key, name, description, pipeline_id, default_stage_id, active
                from forms
                where tenant_id=? and module_key=? and soft_deleted_at is null
                order by name
                """, this::formDto, tenantId, DynamicRecordService.normalizeKey(moduleKey));
    }

    @PostMapping("/api/forms")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public FormDto createForm(@RequestBody FormRequest request) {
        UUID id = jdbc.queryForObject("""
                insert into forms (tenant_id, module_key, name, description, pipeline_id, default_stage_id, active)
                values (?,?,?,?,?,?,?) returning id
                """, UUID.class, TenantContext.requireTenantId(), DynamicRecordService.normalizeKey(request.moduleKey()),
                required(request.name(), "name"), request.description(), request.pipelineId(), request.defaultStageId(),
                request.active());
        syncFormFields(id, request.fieldIds());
        return form(id);
    }

    @PutMapping("/api/forms/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public FormDto updateForm(@PathVariable UUID id, @RequestBody FormRequest request) {
        assertTenantRow("forms", id);
        jdbc.update("""
                update forms
                set name=?, description=?, pipeline_id=?, default_stage_id=?, active=?, updated_at=now()
                where id=? and tenant_id=?
                """, required(request.name(), "name"), request.description(), request.pipelineId(),
                request.defaultStageId(), request.active(), id, TenantContext.requireTenantId());
        syncFormFields(id, request.fieldIds());
        return form(id);
    }

    @DeleteMapping("/api/forms/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void deleteForm(@PathVariable UUID id) {
        jdbc.update("update forms set soft_deleted_at=now(), updated_at=now() where id=? and tenant_id=?",
                id, TenantContext.requireTenantId());
    }

    @PostMapping("/api/forms/{id}/submissions")
    public RecordDto submitForm(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        FormDto form = form(id);
        if (!permissions.hasModuleAction(form.moduleKey(), "create")) {
            throw new AccessDeniedException("Missing create permission for " + form.moduleKey());
        }
        return records.createFromForm(form.moduleKey(), form.pipelineId(), form.defaultStageId(), payload);
    }

    @GetMapping("/api/workflows")
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public List<WorkflowDto> workflows(@RequestParam(required = false) String moduleKey) {
        UUID tenantId = TenantContext.requireTenantId();
        if (moduleKey == null || moduleKey.isBlank()) {
            return jdbc.query("""
                    select id, module_key, name, trigger_type, conditions_json, actions_json, active
                    from workflow_rules where tenant_id=? and soft_deleted_at is null
                    order by module_key, name
                    """, this::workflowDto, tenantId);
        }
        return jdbc.query("""
                select id, module_key, name, trigger_type, conditions_json, actions_json, active
                from workflow_rules where tenant_id=? and module_key=? and soft_deleted_at is null
                order by name
                """, this::workflowDto, tenantId, DynamicRecordService.normalizeKey(moduleKey));
    }

    @PostMapping("/api/workflows")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public WorkflowDto createWorkflow(@RequestBody WorkflowRequest request) {
        String trigger = allowed(request.triggerType(), TRIGGERS, "triggerType");
        UUID id = jdbc.queryForObject("""
                insert into workflow_rules
                (tenant_id, module_key, name, trigger_type, conditions_json, actions_json, active)
                values (?,?,?,?,?,?,?) returning id
                """, UUID.class, TenantContext.requireTenantId(), DynamicRecordService.normalizeKey(request.moduleKey()),
                required(request.name(), "name"), trigger, json(request.conditionsJson(), "{}"),
                json(request.actionsJson(), "[]"), request.active());
        return workflow(id);
    }

    @PutMapping("/api/workflows/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public WorkflowDto updateWorkflow(@PathVariable UUID id, @RequestBody WorkflowRequest request) {
        assertTenantRow("workflow_rules", id);
        jdbc.update("""
                update workflow_rules
                set name=?, trigger_type=?, conditions_json=?, actions_json=?, active=?, updated_at=now()
                where id=? and tenant_id=?
                """, required(request.name(), "name"), allowed(request.triggerType(), TRIGGERS, "triggerType"),
                json(request.conditionsJson(), "{}"), json(request.actionsJson(), "[]"),
                request.active(), id, TenantContext.requireTenantId());
        return workflow(id);
    }

    @DeleteMapping("/api/workflows/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void deleteWorkflow(@PathVariable UUID id) {
        jdbc.update("update workflow_rules set soft_deleted_at=now(), updated_at=now() where id=? and tenant_id=?",
                id, TenantContext.requireTenantId());
    }

    @GetMapping("/api/teams")
    @PreAuthorize("@permissionService.hasAnyPermission('USER_VIEW','SETTINGS_VIEW')")
    public List<TeamDto> teams() {
        return jdbc.query("""
                select id, name, description, active
                from teams where tenant_id=? and soft_deleted_at is null order by name
                """, this::teamDto, TenantContext.requireTenantId());
    }

    @PostMapping("/api/teams")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('USER_ASSIGN_ROLE','SETTINGS_UPDATE')")
    public TeamDto createTeam(@RequestBody TeamRequest request) {
        UUID id = jdbc.queryForObject("""
                insert into teams (tenant_id, name, description, active)
                values (?,?,?,?) returning id
                """, UUID.class, TenantContext.requireTenantId(), required(request.name(), "name"),
                request.description(), request.active());
        return team(id);
    }

    @PutMapping("/api/teams/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('USER_ASSIGN_ROLE','SETTINGS_UPDATE')")
    public TeamDto updateTeam(@PathVariable UUID id, @RequestBody TeamRequest request) {
        assertTenantRow("teams", id);
        jdbc.update("update teams set name=?, description=?, active=?, updated_at=now() where id=? and tenant_id=?",
                required(request.name(), "name"), request.description(), request.active(),
                id, TenantContext.requireTenantId());
        return team(id);
    }

    @DeleteMapping("/api/teams/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('USER_ASSIGN_ROLE','SETTINGS_UPDATE')")
    public void deleteTeam(@PathVariable UUID id) {
        jdbc.update("update teams set soft_deleted_at=now(), updated_at=now() where id=? and tenant_id=?",
                id, TenantContext.requireTenantId());
    }

    @GetMapping("/api/teams/{id}/members")
    @PreAuthorize("@permissionService.hasAnyPermission('USER_VIEW','SETTINGS_VIEW')")
    public List<TeamMemberDto> teamMembers(@PathVariable UUID id) {
        assertTenantRow("teams", id);
        return jdbc.query("""
                select id, team_id, user_id, role_label
                from team_members where tenant_id=? and team_id=? order by created_at
                """, this::teamMemberDto, TenantContext.requireTenantId(), id);
    }

    @PostMapping("/api/teams/{id}/members")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('USER_ASSIGN_ROLE','SETTINGS_UPDATE')")
    public TeamMemberDto addTeamMember(@PathVariable UUID id, @RequestBody TeamMemberRequest request) {
        assertTenantRow("teams", id);
        UUID memberId = jdbc.queryForObject("""
                insert into team_members (tenant_id, team_id, user_id, role_label)
                values (?,?,?,?)
                on conflict (tenant_id, team_id, user_id) do update set role_label=excluded.role_label
                returning id
                """, UUID.class, TenantContext.requireTenantId(), id, request.userId(), request.roleLabel());
        return teamMember(memberId);
    }

    @DeleteMapping("/api/teams/{teamId}/members/{memberId}")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('USER_ASSIGN_ROLE','SETTINGS_UPDATE')")
    public void removeTeamMember(@PathVariable UUID teamId, @PathVariable UUID memberId) {
        assertTenantRow("teams", teamId);
        jdbc.update("delete from team_members where id=? and tenant_id=? and team_id=?",
                memberId, TenantContext.requireTenantId(), teamId);
    }

    @GetMapping("/api/assignment-rules")
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public List<AssignmentRuleDto> assignmentRules(@RequestParam(required = false) String moduleKey) {
        UUID tenantId = TenantContext.requireTenantId();
        if (moduleKey == null || moduleKey.isBlank()) {
            return jdbc.query("""
                    select id, module_key, name, assignment_type, conditions_json, priority_order, active
                    from assignment_rules where tenant_id=? and soft_deleted_at is null
                    order by module_key, priority_order, name
                    """, this::assignmentRuleDto, tenantId);
        }
        return jdbc.query("""
                select id, module_key, name, assignment_type, conditions_json, priority_order, active
                from assignment_rules where tenant_id=? and module_key=? and soft_deleted_at is null
                order by priority_order, name
                """, this::assignmentRuleDto, tenantId, DynamicRecordService.normalizeKey(moduleKey));
    }

    @PostMapping("/api/assignment-rules")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public AssignmentRuleDto createAssignmentRule(@RequestBody AssignmentRuleRequest request) {
        UUID id = jdbc.queryForObject("""
                insert into assignment_rules
                (tenant_id, module_key, name, assignment_type, conditions_json, priority_order, active)
                values (?,?,?,?,?,?,?) returning id
                """, UUID.class, TenantContext.requireTenantId(), DynamicRecordService.normalizeKey(request.moduleKey()),
                required(request.name(), "name"), allowed(request.assignmentType(), ASSIGNMENT_TYPES, "assignmentType"),
                json(request.conditionsJson(), "{}"), request.priorityOrder(), request.active());
        return assignmentRule(id);
    }

    @PutMapping("/api/assignment-rules/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public AssignmentRuleDto updateAssignmentRule(@PathVariable UUID id, @RequestBody AssignmentRuleRequest request) {
        assertTenantRow("assignment_rules", id);
        jdbc.update("""
                update assignment_rules
                set name=?, assignment_type=?, conditions_json=?, priority_order=?, active=?, updated_at=now()
                where id=? and tenant_id=?
                """, required(request.name(), "name"), allowed(request.assignmentType(), ASSIGNMENT_TYPES, "assignmentType"),
                json(request.conditionsJson(), "{}"), request.priorityOrder(), request.active(),
                id, TenantContext.requireTenantId());
        return assignmentRule(id);
    }

    @DeleteMapping("/api/assignment-rules/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void deleteAssignmentRule(@PathVariable UUID id) {
        jdbc.update("update assignment_rules set soft_deleted_at=now(), updated_at=now() where id=? and tenant_id=?",
                id, TenantContext.requireTenantId());
    }

    @GetMapping("/api/dashboards/widgets")
    @PreAuthorize("@permissionService.hasAnyPermission('REPORT_VIEW_COMPANY','REPORT_VIEW_TEAM','REPORT_VIEW_OWN','SETTINGS_VIEW')")
    public List<DashboardWidgetDto> widgets() {
        return jdbc.query("""
                select id, dashboard_id, module_key, title, widget_type, metric_key, filters_json, chart_type, display_order
                from dashboard_widgets where tenant_id=? order by display_order, title
                """, this::widgetDto, TenantContext.requireTenantId());
    }

    @PostMapping("/api/dashboards/widgets")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public DashboardWidgetDto createWidget(@RequestBody DashboardWidgetRequest request) {
        UUID dashboardId = request.dashboardId() != null ? request.dashboardId() : defaultDashboardId();
        UUID id = jdbc.queryForObject("""
                insert into dashboard_widgets
                (tenant_id, dashboard_id, module_key, title, widget_type, metric_key, filters_json, chart_type, display_order)
                values (?,?,?,?,?,?,?,?,?) returning id
                """, UUID.class, TenantContext.requireTenantId(), dashboardId,
                request.moduleKey() == null ? null : DynamicRecordService.normalizeKey(request.moduleKey()),
                required(request.title(), "title"), allowed(request.widgetType(), WIDGET_TYPES, "widgetType"),
                request.metricKey(), json(request.filtersJson(), "{}"), request.chartType(), request.displayOrder());
        return widget(id);
    }

    @PutMapping("/api/dashboards/widgets/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public DashboardWidgetDto updateWidget(@PathVariable UUID id, @RequestBody DashboardWidgetRequest request) {
        assertTenantRow("dashboard_widgets", id);
        jdbc.update("""
                update dashboard_widgets
                set dashboard_id=?, module_key=?, title=?, widget_type=?, metric_key=?,
                    filters_json=?, chart_type=?, display_order=?, updated_at=now()
                where id=? and tenant_id=?
                """, request.dashboardId() == null ? defaultDashboardId() : request.dashboardId(),
                request.moduleKey() == null ? null : DynamicRecordService.normalizeKey(request.moduleKey()),
                required(request.title(), "title"), allowed(request.widgetType(), WIDGET_TYPES, "widgetType"),
                request.metricKey(), json(request.filtersJson(), "{}"), request.chartType(), request.displayOrder(),
                id, TenantContext.requireTenantId());
        return widget(id);
    }

    @DeleteMapping("/api/dashboards/widgets/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void deleteWidget(@PathVariable UUID id) {
        jdbc.update("delete from dashboard_widgets where id=? and tenant_id=?", id, TenantContext.requireTenantId());
    }

    private void syncFormFields(UUID formId, List<UUID> fieldIds) {
        UUID tenantId = TenantContext.requireTenantId();
        jdbc.update("delete from form_fields where form_id=? and tenant_id=?", formId, tenantId);
        if (fieldIds == null) {
            return;
        }
        for (int i = 0; i < fieldIds.size(); i++) {
            UUID fieldId = fieldIds.get(i);
            assertTenantRow("custom_fields", fieldId);
            jdbc.update("""
                    insert into form_fields (tenant_id, form_id, field_id, display_order, required)
                    values (?,?,?,?,false)
                    on conflict (form_id, field_id) do update set display_order=excluded.display_order
                    """, tenantId, formId, fieldId, i);
        }
    }

    private UUID defaultDashboardId() {
        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> existing = jdbc.query("""
                select id from dashboards where tenant_id=? and soft_deleted_at is null
                order by is_default desc, created_at asc limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return jdbc.queryForObject("""
                insert into dashboards (tenant_id, name, description, is_default)
                values (?, 'Default Dashboard', 'Tenant-configured dashboard', true)
                returning id
                """, UUID.class, tenantId);
    }

    private FormDto form(UUID id) {
        return jdbc.query("""
                select id, module_key, name, description, pipeline_id, default_stage_id, active
                from forms where id=? and tenant_id=? and soft_deleted_at is null
                """, this::formDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Form", id));
    }

    private WorkflowDto workflow(UUID id) {
        return jdbc.query("""
                select id, module_key, name, trigger_type, conditions_json, actions_json, active
                from workflow_rules where id=? and tenant_id=? and soft_deleted_at is null
                """, this::workflowDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("WorkflowRule", id));
    }

    private TeamDto team(UUID id) {
        return jdbc.query("""
                select id, name, description, active
                from teams where id=? and tenant_id=? and soft_deleted_at is null
                """, this::teamDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Team", id));
    }

    private TeamMemberDto teamMember(UUID id) {
        return jdbc.query("""
                select id, team_id, user_id, role_label
                from team_members where id=? and tenant_id=?
                """, this::teamMemberDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("TeamMember", id));
    }

    private AssignmentRuleDto assignmentRule(UUID id) {
        return jdbc.query("""
                select id, module_key, name, assignment_type, conditions_json, priority_order, active
                from assignment_rules where id=? and tenant_id=? and soft_deleted_at is null
                """, this::assignmentRuleDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("AssignmentRule", id));
    }

    private DashboardWidgetDto widget(UUID id) {
        return jdbc.query("""
                select id, dashboard_id, module_key, title, widget_type, metric_key, filters_json, chart_type, display_order
                from dashboard_widgets where id=? and tenant_id=?
                """, this::widgetDto, id, TenantContext.requireTenantId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("DashboardWidget", id));
    }

    private FormDto formDto(ResultSet rs, int rowNum) throws SQLException {
        return new FormDto(rs.getObject("id", UUID.class), rs.getString("module_key"), rs.getString("name"),
                rs.getString("description"), rs.getObject("pipeline_id", UUID.class),
                rs.getObject("default_stage_id", UUID.class), rs.getBoolean("active"));
    }

    private WorkflowDto workflowDto(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowDto(rs.getObject("id", UUID.class), rs.getString("module_key"), rs.getString("name"),
                rs.getString("trigger_type"), rs.getString("conditions_json"), rs.getString("actions_json"),
                rs.getBoolean("active"));
    }

    private TeamDto teamDto(ResultSet rs, int rowNum) throws SQLException {
        return new TeamDto(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("description"), rs.getBoolean("active"));
    }

    private TeamMemberDto teamMemberDto(ResultSet rs, int rowNum) throws SQLException {
        return new TeamMemberDto(rs.getObject("id", UUID.class), rs.getObject("team_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("role_label"));
    }

    private AssignmentRuleDto assignmentRuleDto(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentRuleDto(rs.getObject("id", UUID.class), rs.getString("module_key"),
                rs.getString("name"), rs.getString("assignment_type"), rs.getString("conditions_json"),
                rs.getInt("priority_order"), rs.getBoolean("active"));
    }

    private DashboardWidgetDto widgetDto(ResultSet rs, int rowNum) throws SQLException {
        return new DashboardWidgetDto(rs.getObject("id", UUID.class), rs.getObject("dashboard_id", UUID.class),
                rs.getString("module_key"), rs.getString("title"), rs.getString("widget_type"),
                rs.getString("metric_key"), rs.getString("filters_json"), rs.getString("chart_type"),
                rs.getInt("display_order"));
    }

    private void assertTenantRow(String table, UUID id) {
        Long count = jdbc.queryForObject("select count(*) from " + table + " where id=? and tenant_id=?",
                Long.class, id, TenantContext.requireTenantId());
        if (count == null || count == 0) {
            throw new NotFoundException(table, id);
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }

    private String allowed(String value, Set<String> allowed, String label) {
        String normalized = required(value, label).toUpperCase().replace('-', '_');
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("Unsupported " + label + ": " + value);
        }
        return normalized;
    }

    private String json(String value, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value;
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid JSON");
        }
    }
}
