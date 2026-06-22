package com.salescms.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.service.AuditService;
import com.salescms.controller.DashboardLayoutController;
import com.salescms.entity.User;
import com.salescms.dto.RbacCatalog;
import com.salescms.entity.TenantContext;
import com.salescms.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardLayoutControllerIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuditService audit;

    @Test
    void savesReadsResetsAndScopesLayoutToCurrentUser() {
        UUID tenantId = newSalesTenant();
        UUID firstUserId = TenantContext.requireUserId();
        DashboardLayoutController controller = new DashboardLayoutController(jdbc, objectMapper, audit);

        var saved = controller.save("COMPANY_OWNER", new DashboardLayoutController.UserDashboardLayoutRequest(
                List.of("summary-kpis", "pipeline-by-stage"), List.of("registry:abc")));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.widgetOrder()).containsExactly("summary-kpis", "pipeline-by-stage");
        assertThat(saved.hiddenWidgetIds()).containsExactly("registry:abc");

        User secondUser = users.save(new User(tenantId, UUID.randomUUID() + "@test.local",
                passwordEncoder.encode("password123"), "Second", "User", RbacCatalog.COMPANY_OWNER));
        TenantContext.set(tenantId, secondUser.getId());
        assertThat(controller.get("COMPANY_OWNER").id()).isNull();

        TenantContext.set(tenantId, firstUserId);
        assertThat(controller.get("COMPANY_OWNER").id()).isEqualTo(saved.id());
        controller.reset("COMPANY_OWNER");
        assertThat(controller.get("COMPANY_OWNER").id()).isNull();

        Long auditCount = jdbc.queryForObject("""
                select count(*) from audit_log
                where tenant_id=? and actor_user_id=? and object_type='DASHBOARD_LAYOUT'
                """, Long.class, tenantId, firstUserId);
        assertThat(auditCount).isEqualTo(2);
    }
}

