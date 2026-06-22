package com.salescms.controller;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.service.PermissionService;
import com.salescms.entity.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/phase7")
public class Phase7Controller {

    private static final String CRM_READ = "@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED','REPORT_VIEW_COMPANY','REPORT_VIEW_TEAM','REPORT_VIEW_OWN')";
    private static final String APPROVAL_WRITE = "@permissionService.hasAnyPermission('SETTINGS_UPDATE','LEAD_UPDATE','PO_UPDATE')";
    private static final String REPORT_READ = "@permissionService.hasAnyPermission('REPORT_VIEW_COMPANY','REPORT_VIEW_TEAM','REPORT_VIEW_OWN')";
    private static final String REPORT_WRITE = "@permissionService.hasAnyPermission('REPORT_EXPORT','SETTINGS_UPDATE')";
    private static final String BRANDING_READ = "@permissionService.hasAnyPermission('SETTINGS_VIEW','COMPANY_MANAGE_SETTINGS')";
    private static final String BRANDING_WRITE = "@permissionService.hasAnyPermission('SETTINGS_UPDATE','COMPANY_MANAGE_SETTINGS')";
    private static final Set<String> APPROVAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");
    private static final Set<String> REPORT_VISIBILITIES = Set.of("COMPANY", "TEAM", "PRIVATE");

    public record LeadScoreDto(UUID recordId, String title, String status, String source,
                               String priority, String ownerName, int score, String grade,
                               String reason, Instant updatedAt) {
    }

    public record NextBestActionDto(String id, String type, String title, String reason,
                                    String priority, UUID recordId, UUID approvalId, Instant dueAt) {
    }

    public record ApprovalDto(UUID id, UUID recordId, String recordTitle, String title,
                              String category, BigDecimal amount, String currency, String status,
                              UUID requesterUserId, String requesterName, UUID approverUserId,
                              String approverName, String decisionNote, Instant createdAt,
                              Instant updatedAt) {
    }

    public record ApprovalRequest(UUID id, UUID recordId, String title, String category,
                                  BigDecimal amount, String currency, UUID approverUserId) {
    }

    public record ApprovalDecisionRequest(String status, String note) {
    }

    public record SavedReportDto(UUID id, String name, String description, String reportType,
                                 String configJson, String visibility, Instant updatedAt) {
    }

    public record SavedReportRequest(UUID id, String name, String description, String reportType,
                                     String configJson, String visibility) {
    }

    public record ReportMetricDto(String label, long count, BigDecimal value) {
    }

    public record AdvancedReportsDto(long leads, long hotLeads, long openDeals,
                                     BigDecimal pipelineValue, BigDecimal wonValue,
                                     long pendingApprovals, long overdueTasks,
                                     List<ReportMetricDto> funnel, List<SavedReportDto> savedReports) {
    }

    public record BrandingDto(UUID tenantId, String brandName, String logoUrl, String primaryColor,
                              String accentColor, String fontFamily, String customDomain,
                              Instant updatedAt) {
    }

    public record BrandingRequest(String brandName, String logoUrl, String primaryColor,
                                  String accentColor, String fontFamily, String customDomain) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PermissionService permissions;

    public Phase7Controller(JdbcTemplate jdbc, AuditService audit, PermissionService permissions) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.permissions = permissions;
    }

    @GetMapping("/lead-scoring")
    @PreAuthorize(CRM_READ)
    public List<LeadScoreDto> leadScoring() {
        return leadScores().stream()
                .sorted(Comparator.comparingInt(LeadScoreDto::score).reversed()
                        .thenComparing(LeadScoreDto::updatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @GetMapping("/next-best-actions")
    @PreAuthorize(CRM_READ)
    public List<NextBestActionDto> nextBestActions() {
        UUID tenantId = TenantContext.requireTenantId();
        ArrayList<NextBestActionDto> actions = new ArrayList<>();
        leadScores().stream()
                .filter(score -> score.score() >= 70)
                .limit(10)
                .forEach(score -> actions.add(new NextBestActionDto(
                        stableId("lead", score.recordId()),
                        "LEAD_FOLLOW_UP",
                        "Work " + score.title(),
                        score.reason(),
                        score.score() >= 85 ? "HIGH" : "NORMAL",
                        score.recordId(),
                        null,
                        Instant.now().plus(Duration.ofHours(score.score() >= 85 ? 4 : 24)))));

        jdbc.query("""
                select id, title, category, amount, currency, updated_at
                from approval_requests
                where tenant_id=? and soft_deleted_at is null and status='PENDING'
                order by updated_at asc
                limit 8
                """, rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    actions.add(new NextBestActionDto(
                            stableId("approval", id),
                            "APPROVAL_REVIEW",
                            "Review approval: " + rs.getString("title"),
                            "Pending " + labelize(rs.getString("category")) + " approval is waiting for a decision.",
                            rs.getBigDecimal("amount") != null && rs.getBigDecimal("amount").compareTo(BigDecimal.valueOf(50000)) >= 0
                                    ? "HIGH" : "NORMAL",
                            null,
                            id,
                            rs.getTimestamp("updated_at").toInstant().plus(Duration.ofDays(1))));
                }, tenantId);

        jdbc.query("""
                select id, title, related_object_id, due_at
                from tasks
                where tenant_id=? and soft_deleted_at is null and status='OPEN'
                  and due_at is not null and due_at < now()
                order by due_at asc
                limit 8
                """, rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    actions.add(new NextBestActionDto(
                            stableId("task", id),
                            "OVERDUE_TASK",
                            "Recover overdue task: " + rs.getString("title"),
                            "Task is past due and should be closed or rescheduled.",
                            "HIGH",
                            rs.getObject("related_object_id", UUID.class),
                            null,
                            rs.getTimestamp("due_at").toInstant()));
                }, tenantId);

        return actions.stream()
                .sorted(Comparator.comparing(NextBestActionDto::priority).thenComparing(NextBestActionDto::dueAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(20)
                .toList();
    }

    @GetMapping("/approvals")
    @PreAuthorize(CRM_READ)
    public List<ApprovalDto> approvals(@RequestParam(required = false) String status) {
        String normalizedStatus = status == null || status.isBlank() ? null : normalize(status, "status", APPROVAL_STATUSES);
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                select a.id, a.record_id, r.title record_title, a.title, a.category, a.amount, a.currency,
                       a.status, a.requester_user_id,
                       coalesce(nullif(trim(coalesce(req.first_name, '') || ' ' || coalesce(req.last_name, '')), ''), req.email) requester_name,
                       a.approver_user_id,
                       coalesce(nullif(trim(coalesce(app.first_name, '') || ' ' || coalesce(app.last_name, '')), ''), app.email) approver_name,
                       a.decision_note, a.created_at, a.updated_at
                from approval_requests a
                left join crm_records r on r.id=a.record_id and r.tenant_id=a.tenant_id
                left join users req on req.id=a.requester_user_id
                left join users app on app.id=a.approver_user_id
                where a.tenant_id=? and a.soft_deleted_at is null
                  and (? is null or a.status=?)
                order by case a.status when 'PENDING' then 1 when 'APPROVED' then 2 when 'REJECTED' then 3 else 4 end,
                         a.updated_at desc
                """, this::approvalDto, tenantId, normalizedStatus, normalizedStatus);
    }

    @PostMapping("/approvals")
    @Transactional
    @PreAuthorize(APPROVAL_WRITE)
    public ApprovalDto saveApproval(@RequestBody ApprovalRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String title = requiredText(request.title(), "title");
        String category = defaultText(request.category(), "GENERAL").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        String currency = defaultText(request.currency(), "USD").toUpperCase(Locale.ROOT);
        if (request.recordId() != null) {
            requireRecord(request.recordId(), tenantId);
        }
        if (request.approverUserId() != null) {
            requireTenantUser(request.approverUserId(), tenantId);
        }
        if (request.id() == null) {
            jdbc.update("""
                    insert into approval_requests
                        (id, tenant_id, record_id, title, category, amount, currency,
                         status, requester_user_id, approver_user_id, created_by, updated_by)
                    values (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, tenantId, request.recordId(), title, category, request.amount(), currency,
                    "PENDING", userId, request.approverUserId(), userId, userId);
            audit.record("APPROVAL_CREATE", "APPROVAL", id, Map.of("category", category));
        } else {
            int updated = jdbc.update("""
                    update approval_requests
                    set record_id=?, title=?, category=?, amount=?, currency=?, approver_user_id=?,
                        updated_at=now(), updated_by=?, version=version+1
                    where id=? and tenant_id=? and soft_deleted_at is null
                    """, request.recordId(), title, category, request.amount(), currency,
                    request.approverUserId(), userId, id, tenantId);
            if (updated == 0) {
                throw new NotFoundException("Approval", id);
            }
            audit.record("APPROVAL_UPDATE", "APPROVAL", id, Map.of("category", category));
        }
        return findApproval(id);
    }

    @PostMapping("/approvals/{id}/decision")
    @Transactional
    @PreAuthorize(APPROVAL_WRITE)
    public ApprovalDto decideApproval(@PathVariable UUID id, @RequestBody ApprovalDecisionRequest request) {
        String status = normalize(request.status(), "status", Set.of("APPROVED", "REJECTED", "CANCELLED"));
        UUID tenantId = TenantContext.requireTenantId();
        int updated = jdbc.update("""
                update approval_requests
                set status=?, decision_note=?, approver_user_id=coalesce(approver_user_id, ?),
                    updated_at=now(), updated_by=?, version=version+1
                where id=? and tenant_id=? and soft_deleted_at is null
                """, status, request.note(), TenantContext.requireUserId(), TenantContext.requireUserId(), id, tenantId);
        if (updated == 0) {
            throw new NotFoundException("Approval", id);
        }
        audit.record("APPROVAL_DECISION", "APPROVAL", id, Map.of("status", status));
        return findApproval(id);
    }

    @GetMapping("/advanced-reports")
    @PreAuthorize(REPORT_READ)
    public AdvancedReportsDto advancedReports() {
        UUID tenantId = TenantContext.requireTenantId();
        long leads = scalar("select count(*) from crm_records where tenant_id=? and module_key='leads' and soft_deleted_at is null", tenantId);
        long hotLeads = leadScores().stream().filter(row -> row.score() >= 80).count();
        long openDeals = scalar("select count(*) from crm_records where tenant_id=? and module_key='opportunities' and status='OPEN' and soft_deleted_at is null", tenantId);
        BigDecimal pipelineValue = money("select coalesce(sum(amount),0) from crm_records where tenant_id=? and module_key='opportunities' and status='OPEN' and soft_deleted_at is null", tenantId);
        BigDecimal wonValue = money("select coalesce(sum(amount),0) from crm_records where tenant_id=? and module_key='opportunities' and status='WON' and soft_deleted_at is null", tenantId);
        long pendingApprovals = scalar("select count(*) from approval_requests where tenant_id=? and status='PENDING' and soft_deleted_at is null", tenantId);
        long overdueTasks = scalar("select count(*) from tasks where tenant_id=? and status='OPEN' and soft_deleted_at is null and due_at < now()", tenantId);
        List<ReportMetricDto> funnel = jdbc.query("""
                select module_key || ':' || status label, count(*) count, coalesce(sum(amount),0) value
                from crm_records
                where tenant_id=? and module_key in ('leads','opportunities') and soft_deleted_at is null
                group by module_key, status
                order by module_key, count desc
                """, (rs, row) -> new ReportMetricDto(rs.getString("label"), rs.getLong("count"), rs.getBigDecimal("value")), tenantId);
        return new AdvancedReportsDto(leads, hotLeads, openDeals, pipelineValue, wonValue,
                pendingApprovals, overdueTasks, funnel, savedReports());
    }

    @GetMapping("/saved-reports")
    @PreAuthorize(REPORT_READ)
    public List<SavedReportDto> savedReports() {
        return jdbc.query("""
                select id, name, description, report_type, config_json, visibility, updated_at
                from saved_reports
                where tenant_id=? and soft_deleted_at is null
                order by updated_at desc, name asc
                """, this::savedReportDto, TenantContext.requireTenantId());
    }

    @PostMapping("/saved-reports")
    @Transactional
    @PreAuthorize(REPORT_WRITE)
    public SavedReportDto saveReport(@RequestBody SavedReportRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String name = requiredText(request.name(), "name");
        String reportType = defaultText(request.reportType(), "PIPELINE").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        String visibility = normalize(defaultText(request.visibility(), "COMPANY"), "visibility", REPORT_VISIBILITIES);
        String configJson = defaultText(request.configJson(), "{}");
        if (request.id() == null) {
            jdbc.update("""
                    insert into saved_reports
                        (id, tenant_id, name, description, report_type, config_json, visibility, created_by, updated_by)
                    values (?,?,?,?,?,?,?,?,?)
                    """, id, tenantId, name, request.description(), reportType, configJson, visibility, userId, userId);
            audit.record("SAVED_REPORT_CREATE", "SAVED_REPORT", id, Map.of("reportType", reportType));
        } else {
            int updated = jdbc.update("""
                    update saved_reports
                    set name=?, description=?, report_type=?, config_json=?, visibility=?,
                        updated_at=now(), updated_by=?, version=version+1
                    where id=? and tenant_id=? and soft_deleted_at is null
                    """, name, request.description(), reportType, configJson, visibility, userId, id, tenantId);
            if (updated == 0) {
                throw new NotFoundException("SavedReport", id);
            }
            audit.record("SAVED_REPORT_UPDATE", "SAVED_REPORT", id, Map.of("reportType", reportType));
        }
        return findSavedReport(id);
    }

    @GetMapping("/branding")
    @PreAuthorize(BRANDING_READ)
    public BrandingDto branding() {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                select b.tenant_id, b.brand_name, b.logo_url, b.primary_color, b.accent_color,
                       b.font_family, b.custom_domain, b.updated_at
                from tenant_branding b
                where b.tenant_id=?
                """, this::brandingDto, tenantId).stream()
                .findFirst()
                .orElseGet(() -> defaultBranding(tenantId));
    }

    @PutMapping("/branding")
    @Transactional
    @PreAuthorize(BRANDING_WRITE)
    public BrandingDto updateBranding(@RequestBody BrandingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        String brandName = requiredText(request.brandName(), "brandName");
        String primary = defaultText(request.primaryColor(), "#4F46E5");
        String accent = defaultText(request.accentColor(), "#10B981");
        String font = defaultText(request.fontFamily(), "Inter");
        jdbc.update("""
                insert into tenant_branding
                    (tenant_id, brand_name, logo_url, primary_color, accent_color, font_family, custom_domain, updated_by)
                values (?,?,?,?,?,?,?,?)
                on conflict (tenant_id) do update set
                    brand_name=excluded.brand_name,
                    logo_url=excluded.logo_url,
                    primary_color=excluded.primary_color,
                    accent_color=excluded.accent_color,
                    font_family=excluded.font_family,
                    custom_domain=excluded.custom_domain,
                    updated_by=excluded.updated_by,
                    updated_at=now()
                """, tenantId, brandName, request.logoUrl(), primary, accent, font, request.customDomain(), userId);
        audit.record("TENANT_BRANDING_UPDATE", "TENANT_BRANDING", tenantId,
                Map.of("brandName", brandName, "primaryColor", primary));
        return branding();
    }

    private List<LeadScoreDto> leadScores() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID owner = assignedOnly() ? TenantContext.requireUserId() : null;
        return jdbc.query("""
                select r.id record_id, r.title, r.status, r.source, r.priority, r.amount,
                       r.owner_user_id,
                       coalesce(nullif(trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')), ''), u.email) owner_name,
                       r.created_at, r.updated_at
                from crm_records r
                left join users u on u.id=r.owner_user_id
                where r.tenant_id=? and r.module_key='leads' and r.soft_deleted_at is null
                  and (? is null or r.owner_user_id=?)
                order by r.updated_at desc, r.created_at desc
                limit 200
                """, this::leadScoreDto, tenantId, owner, owner);
    }

    private boolean assignedOnly() {
        if (permissions.hasAnyPermission("LEAD_VIEW_ALL", "LEAD_VIEW_TEAM", "REPORT_VIEW_COMPANY", "REPORT_VIEW_TEAM")) {
            return false;
        }
        return permissions.hasPermission("LEAD_VIEW_ASSIGNED") || permissions.hasPermission("REPORT_VIEW_OWN");
    }

    private LeadScoreDto leadScoreDto(ResultSet rs, int row) throws SQLException {
        String status = rs.getString("status");
        String source = rs.getString("source");
        String priority = rs.getString("priority");
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        int score = 45;
        if ("CONVERTED".equals(status)) score += 35;
        else if ("QUALIFIED".equals(status)) score += 28;
        else if ("WORKING".equals(status)) score += 18;
        else if ("NEW".equals(status)) score += 12;
        if ("HIGH".equals(priority)) score += 10;
        else if ("NORMAL".equals(priority)) score += 4;
        if (source != null && (source.toLowerCase(Locale.ROOT).contains("web")
                || source.toLowerCase(Locale.ROOT).contains("referral"))) {
            score += 6;
        }
        if (rs.getBigDecimal("amount") != null) score += 5;
        if (rs.getObject("owner_user_id", UUID.class) != null) score += 4;
        long ageDays = Duration.between(updatedAt, Instant.now()).toDays();
        if (ageDays <= 2) score += 8;
        else if (ageDays <= 7) score += 4;
        else score -= Math.min(12, (int) ageDays / 7);
        score = Math.max(0, Math.min(100, score));
        String grade = score >= 85 ? "HOT" : score >= 70 ? "WARM" : score >= 50 ? "NURTURE" : "LOW";
        String reason = grade.equals("HOT")
                ? "High-fit lead with recent activity and strong conversion signals."
                : grade.equals("WARM")
                    ? "Worth active follow-up based on status, owner coverage, and recency."
                    : "Keep in nurture until engagement or qualification improves.";
        return new LeadScoreDto(
                rs.getObject("record_id", UUID.class),
                rs.getString("title"),
                status,
                source,
                priority,
                rs.getString("owner_name"),
                score,
                grade,
                reason,
                updatedAt);
    }

    private ApprovalDto findApproval(UUID id) {
        return approvals(null).stream()
                .filter(approval -> approval.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Approval", id));
    }

    private SavedReportDto findSavedReport(UUID id) {
        return savedReports().stream()
                .filter(report -> report.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("SavedReport", id));
    }

    private void requireRecord(UUID recordId, UUID tenantId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from crm_records
                where id=? and tenant_id=? and soft_deleted_at is null
                """, Integer.class, recordId, tenantId);
        if (count == null || count == 0) {
            throw new NotFoundException("Record", recordId);
        }
    }

    private void requireTenantUser(UUID userId, UUID tenantId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from users
                where id=? and tenant_id=? and is_deleted=false
                """, Integer.class, userId, tenantId);
        if (count == null || count == 0) {
            throw new NotFoundException("User", userId);
        }
    }

    private BrandingDto defaultBranding(UUID tenantId) {
        String tenantName = jdbc.queryForObject("select name from tenants where id=?", String.class, tenantId);
        return new BrandingDto(tenantId, tenantName, null, "#4F46E5", "#10B981", "Inter", null, Instant.now());
    }

    private String normalize(String value, String label, Set<String> allowed) {
        String normalized = requiredText(value, label).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BadRequestException("Unsupported " + label + ": " + value);
        }
        return normalized;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String stableId(String prefix, UUID id) {
        return UUID.nameUUIDFromBytes((prefix + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ApprovalDto approvalDto(ResultSet rs, int row) throws SQLException {
        return new ApprovalDto(
                rs.getObject("id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getString("record_title"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getObject("requester_user_id", UUID.class),
                rs.getString("requester_name"),
                rs.getObject("approver_user_id", UUID.class),
                rs.getString("approver_name"),
                rs.getString("decision_note"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private SavedReportDto savedReportDto(ResultSet rs, int row) throws SQLException {
        return new SavedReportDto(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("report_type"),
                rs.getString("config_json"),
                rs.getString("visibility"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private BrandingDto brandingDto(ResultSet rs, int row) throws SQLException {
        return new BrandingDto(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("brand_name"),
                rs.getString("logo_url"),
                rs.getString("primary_color"),
                rs.getString("accent_color"),
                rs.getString("font_family"),
                rs.getString("custom_domain"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String labelize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
