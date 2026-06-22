package com.salescms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.entity.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/user-layout")
public class DashboardLayoutController {

    private static final String VIEW_PERMISSIONS = """
            @permissionService.hasAnyPermission(
            'LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED',
            'REPORT_VIEW_COMPANY','REPORT_VIEW_TEAM','REPORT_VIEW_OWN',
            'PLATFORM_DASHBOARD_VIEW','SETTINGS_VIEW')
            """;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    public record UserDashboardLayoutDto(UUID id, String dashboardKey, List<String> widgetOrder,
                                         List<String> hiddenWidgetIds, Instant updatedAt) {
    }

    public record UserDashboardLayoutRequest(List<String> widgetOrder, List<String> hiddenWidgetIds) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public DashboardLayoutController(JdbcTemplate jdbc, ObjectMapper objectMapper, AuditService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize(VIEW_PERMISSIONS)
    public UserDashboardLayoutDto get(@RequestParam String dashboardKey) {
        String key = dashboardKey(dashboardKey);
        return find(key).stream()
                .findFirst()
                .orElse(new UserDashboardLayoutDto(null, key, List.of(), List.of(), null));
    }

    @PutMapping
    @Transactional
    @PreAuthorize(VIEW_PERMISSIONS)
    public UserDashboardLayoutDto save(@RequestParam String dashboardKey,
                                       @RequestBody UserDashboardLayoutRequest request) {
        String key = dashboardKey(dashboardKey);
        List<String> widgetOrder = widgetIds(request.widgetOrder());
        List<String> hiddenWidgetIds = widgetIds(request.hiddenWidgetIds());
        String orderJson = json(widgetOrder);
        String hiddenJson = json(hiddenWidgetIds);
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        UUID id = jdbc.queryForObject("""
                insert into user_dashboard_layouts
                    (tenant_id, user_id, dashboard_key, widget_order_json, hidden_widget_ids_json,
                     owner_user_id, created_by, updated_by)
                values (?,?,?,?,?,?,?,?)
                on conflict (tenant_id, user_id, dashboard_key) do update
                set widget_order_json=excluded.widget_order_json,
                    hidden_widget_ids_json=excluded.hidden_widget_ids_json,
                    updated_by=excluded.updated_by,
                    updated_at=now(),
                    soft_deleted_at=null,
                    version=user_dashboard_layouts.version + 1
                returning id
                """, UUID.class, tenantId, userId, key, orderJson, hiddenJson, userId, userId, userId);
        audit.record("UPDATE", "DASHBOARD_LAYOUT", id, Map.of("dashboardKey", key));
        return find(key).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Dashboard layout was not saved"));
    }

    @DeleteMapping
    @Transactional
    @PreAuthorize(VIEW_PERMISSIONS)
    public void reset(@RequestParam String dashboardKey) {
        String key = dashboardKey(dashboardKey);
        find(key).stream().findFirst().ifPresent(existing -> {
            jdbc.update("""
                    update user_dashboard_layouts
                    set soft_deleted_at=now(), updated_at=now(), updated_by=?
                    where id=? and tenant_id=? and user_id=?
                    """, TenantContext.requireUserId(), existing.id(), TenantContext.requireTenantId(),
                    TenantContext.requireUserId());
            audit.record("DELETE", "DASHBOARD_LAYOUT", existing.id(), Map.of("dashboardKey", key));
        });
    }

    private List<UserDashboardLayoutDto> find(String dashboardKey) {
        return jdbc.query("""
                select id, dashboard_key, widget_order_json, hidden_widget_ids_json, updated_at
                from user_dashboard_layouts
                where tenant_id=? and user_id=? and dashboard_key=? and soft_deleted_at is null
                """, this::layoutDto, TenantContext.requireTenantId(), TenantContext.requireUserId(), dashboardKey);
    }

    private UserDashboardLayoutDto layoutDto(ResultSet rs, int rowNum) throws SQLException {
        return new UserDashboardLayoutDto(
                rs.getObject("id", UUID.class),
                rs.getString("dashboard_key"),
                readList(rs.getString("widget_order_json")),
                readList(rs.getString("hidden_widget_ids_json")),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String dashboardKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("dashboardKey is required");
        }
        String key = value.trim();
        if (key.length() > 120 || !key.matches("[A-Za-z0-9_.:-]+")) {
            throw new BadRequestException("Invalid dashboardKey");
        }
        return key;
    }

    private List<String> widgetIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String id = value.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (id.length() > 160 || !id.matches("[A-Za-z0-9_.:-]+")) {
                throw new BadRequestException("Invalid widget id");
            }
            ids.add(id);
            if (ids.size() > 100) {
                throw new BadRequestException("Too many dashboard widgets");
            }
        }
        return List.copyOf(ids);
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid dashboard layout");
        }
    }

    private List<String> readList(String json) {
        try {
            return widgetIds(objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, STRING_LIST));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
