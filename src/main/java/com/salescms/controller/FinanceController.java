package com.salescms.controller;
import com.salescms.service.FinanceHandoffService;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sales-to-finance handoff (spec Phase 5). Won opportunities enter
 * {@code finance_handoff_status = AWAITING_INVOICE} (see {@code FinanceHandoffService}); this
 * controller lets finance list and advance that queue. All queries are tenant-scoped, RBAC
 * gated, and mutations are audited per the project hard rules.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private static final String VIEW = "@permissionService.hasAnyPermission("
            + "'PO_VIEW_ALL','BILLING_VIEW','BILLING_MANAGE','REPORT_VIEW_COMPANY')";
    private static final String MANAGE = "@permissionService.hasAnyPermission('PO_UPDATE','BILLING_MANAGE')";

    private static final Set<String> STATUSES = Set.of("AWAITING_INVOICE", "INVOICE_DRAFTED", "COMPLETED");
    private static final Map<String, String> NEXT_STATUS = Map.of(
            "AWAITING_INVOICE", "INVOICE_DRAFTED",
            "INVOICE_DRAFTED", "COMPLETED");

    public record FinanceHandoffDto(UUID recordId, String title, BigDecimal amount, String currency,
                                    UUID ownerUserId, String ownerName, String status, Instant handoffAt) {
    }

    public record FinanceHandoffSummaryDto(long awaitingCount, long draftedCount, long completedCount,
                                           BigDecimal awaitingValue) {
    }

    public record AdvanceHandoffRequest(String status) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public FinanceController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static final String HANDOFF_SELECT = """
            select r.id, r.title, r.amount, r.currency, r.owner_user_id,
                   coalesce(u.first_name || ' ' || u.last_name, 'Unassigned') owner_name,
                   r.finance_handoff_status, r.finance_handoff_at
            from crm_records r
            left join users u on u.id = r.owner_user_id
            where r.tenant_id=? and r.module_key='opportunities'
              and r.soft_deleted_at is null and r.finance_handoff_status is not null
            """;
    private static final String HANDOFF_ORDER = " order by r.finance_handoff_at desc nulls last, r.updated_at desc";

    /** Won deals in the finance handoff pipeline, newest first; optional status filter. */
    @GetMapping("/handoffs")
    @PreAuthorize(VIEW)
    public List<FinanceHandoffDto> handoffs(@RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.requireTenantId();
        if (status != null && !status.isBlank()) {
            return jdbc.query(HANDOFF_SELECT + " and r.finance_handoff_status=?" + HANDOFF_ORDER,
                    this::handoffDto, tenantId, normalizeStatus(status));
        }
        return jdbc.query(HANDOFF_SELECT + HANDOFF_ORDER, this::handoffDto, tenantId);
    }

    /** Counts and awaiting value across the handoff statuses. */
    @GetMapping("/summary")
    @PreAuthorize(VIEW)
    public FinanceHandoffSummaryDto summary() {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.queryForObject("""
                select
                  count(*) filter (where finance_handoff_status='AWAITING_INVOICE') awaiting_count,
                  count(*) filter (where finance_handoff_status='INVOICE_DRAFTED') drafted_count,
                  count(*) filter (where finance_handoff_status='COMPLETED') completed_count,
                  coalesce(sum(amount) filter (where finance_handoff_status='AWAITING_INVOICE'),0) awaiting_value
                from crm_records
                where tenant_id=? and module_key='opportunities'
                  and soft_deleted_at is null and finance_handoff_status is not null
                """, (rs, n) -> new FinanceHandoffSummaryDto(
                        rs.getLong("awaiting_count"), rs.getLong("drafted_count"),
                        rs.getLong("completed_count"), rs.getBigDecimal("awaiting_value")),
                tenantId);
    }

    /** Advance a handoff to a new status (e.g. AWAITING_INVOICE -> INVOICE_DRAFTED -> COMPLETED). */
    @PostMapping("/handoffs/{recordId}/advance")
    @Transactional
    @PreAuthorize(MANAGE)
    public FinanceHandoffDto advance(@PathVariable UUID recordId, @RequestBody AdvanceHandoffRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        String status = normalizeStatus(request == null ? null : request.status());
        String currentStatus = currentStatus(tenantId, recordId);
        requireSequentialTransition(currentStatus, status);
        int updated = jdbc.update("""
                update crm_records
                set finance_handoff_status=?, updated_at=now(), updated_by=?
                where id=? and tenant_id=? and module_key='opportunities'
                  and soft_deleted_at is null and finance_handoff_status=?
                """, status, userId, recordId, tenantId, currentStatus);
        if (updated == 0) {
            throw new NotFoundException("FinanceHandoff", recordId);
        }
        audit.record("FINANCE_HANDOFF_UPDATE", "CRM_RECORD", recordId,
                Map.of("from", currentStatus, "to", status));
        return findHandoff(tenantId, recordId);
    }

    private String currentStatus(UUID tenantId, UUID recordId) {
        return jdbc.query("""
                select r.finance_handoff_status
                from crm_records r
                where r.id=? and r.tenant_id=? and r.module_key='opportunities'
                  and r.soft_deleted_at is null and r.finance_handoff_status is not null
                """, (rs, n) -> rs.getString("finance_handoff_status"), recordId, tenantId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("FinanceHandoff", recordId));
    }

    private void requireSequentialTransition(String currentStatus, String requestedStatus) {
        String expected = NEXT_STATUS.get(currentStatus);
        if (expected == null) {
            throw new BadRequestException("Finance handoff is already completed");
        }
        if (!expected.equals(requestedStatus)) {
            throw new BadRequestException("Finance handoff can only move from "
                    + currentStatus + " to " + expected);
        }
    }

    private FinanceHandoffDto findHandoff(UUID tenantId, UUID recordId) {
        return jdbc.query(HANDOFF_SELECT + " and r.id=?", this::handoffDto, tenantId, recordId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("FinanceHandoff", recordId));
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("status is required");
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        if (!STATUSES.contains(normalized)) {
            throw new BadRequestException("Unsupported handoff status: " + value);
        }
        return normalized;
    }

    private FinanceHandoffDto handoffDto(ResultSet rs, int rowNum) throws SQLException {
        Instant handoffAt = rs.getTimestamp("finance_handoff_at") == null
                ? null : rs.getTimestamp("finance_handoff_at").toInstant();
        return new FinanceHandoffDto(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("owner_name"),
                rs.getString("finance_handoff_status"),
                handoffAt);
    }
}
