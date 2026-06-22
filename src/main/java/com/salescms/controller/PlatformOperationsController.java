package com.salescms.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
public class PlatformOperationsController {

    private static final String PLATFORM_READ = "@permissionService.hasAnyPermission('PLATFORM_DASHBOARD_VIEW','PLATFORM_SUPPORT_ACCESS')";
    private static final String SUPPORT = "@permissionService.hasPermission('PLATFORM_SUPPORT_ACCESS')";
    private static final String DASHBOARD = "@permissionService.hasPermission('PLATFORM_DASHBOARD_VIEW')";
    private static final Set<String> TICKET_PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> TICKET_STATUSES = Set.of("OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED");
    private static final Set<String> ANNOUNCEMENT_STATUSES = Set.of("DRAFT", "SCHEDULED", "SENT");
    private static final Set<String> ANNOUNCEMENT_AUDIENCES = Set.of("ALL_TENANTS", "ACTIVE_TENANTS", "TRIAL_TENANTS", "PAST_DUE_TENANTS");

    public record SupportTicketDto(UUID id, UUID tenantId, String tenantName, String tenantSlug,
                                   String subject, String category, String priority, String status,
                                   String description, UUID ownerUserId, String ownerName,
                                   Instant createdAt, Instant updatedAt) {
    }

    public record SupportTicketRequest(UUID id, UUID tenantId, String subject, String category,
                                       String priority, String status, String description,
                                       UUID ownerUserId) {
    }

    public record SupportTicketStatusRequest(String status) {
    }

    public record OnboardingTenantDto(UUID tenantId, String tenantName, String tenantSlug,
                                      String tenantStatus, Instant createdAt, long users,
                                      long activeUsers, String planName, String subscriptionStatus,
                                      long openTickets, int score, String stage, String nextAction) {
    }

    public record TenantUsageDto(UUID tenantId, String tenantName, String tenantSlug, String tenantStatus,
                                 long users, long activeUsers, long leads, long opportunities,
                                 long openDeals, long wonDeals, long purchaseOrders, long openTickets,
                                 String planName, String subscriptionStatus, BigDecimal monthlyPrice,
                                 String currency) {
    }

    public record UsageAnalyticsDto(long tenants, long activeTenants, long users, long activeUsers,
                                    long leads, long opportunities, long openTickets,
                                    List<TenantUsageDto> tenantUsage) {
    }

    public record PlanBreakdownDto(String planName, long tenants, BigDecimal monthlyRevenue) {
    }

    public record GrowthBucketDto(String month, long tenants) {
    }

    public record GlobalAnalyticsDto(BigDecimal monthlyRecurringRevenue, long activeSubscriptions,
                                     long trialSubscriptions, List<PlanBreakdownDto> planBreakdown,
                                     List<GrowthBucketDto> tenantGrowth) {
    }

    public record AnnouncementDto(UUID id, String title, String body, String audience, String status,
                                  Instant sendAt, Instant sentAt, Instant createdAt, Instant updatedAt) {
    }

    public record AnnouncementRequest(UUID id, String title, String body, String audience,
                                      String status, Instant sendAt) {
    }

    public record KnowledgeArticleDto(UUID id, String title, String category, String summary,
                                      String body, Instant updatedAt) {
    }

    public record KnowledgeArticleRequest(UUID id, String title, String category, String summary,
                                          String body) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PlatformOperationsController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/support-tickets")
    @PreAuthorize(SUPPORT)
    public List<SupportTicketDto> supportTickets(@RequestParam(required = false) String status) {
        String normalizedStatus = status == null || status.isBlank() ? null : normalize(status, "status", TICKET_STATUSES);
        return jdbc.query("""
                select st.id, st.tenant_id, t.name tenant_name, t.slug tenant_slug,
                       st.subject, st.category, st.priority, st.status, st.description,
                       st.owner_user_id,
                       coalesce(nullif(trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')), ''), u.email) owner_name,
                       st.created_at, st.updated_at
                from platform_support_tickets st
                join tenants t on t.id = st.tenant_id
                left join users u on u.id = st.owner_user_id
                where st.soft_deleted_at is null
                  and (? is null or st.status = ?)
                order by case st.priority when 'URGENT' then 1 when 'HIGH' then 2 when 'NORMAL' then 3 else 4 end,
                         st.updated_at desc
                """, this::supportTicketDto, normalizedStatus, normalizedStatus);
    }

    @PostMapping("/support-tickets")
    @Transactional
    @PreAuthorize(SUPPORT)
    public SupportTicketDto saveSupportTicket(@RequestBody SupportTicketRequest request) {
        UUID userId = TenantContext.requireUserId();
        UUID tenantId = requireTenant(request.tenantId());
        String subject = requiredText(request.subject(), "subject");
        String category = defaultText(request.category(), "GENERAL").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        String priority = normalize(defaultText(request.priority(), "NORMAL"), "priority", TICKET_PRIORITIES);
        String status = normalize(defaultText(request.status(), "OPEN"), "status", TICKET_STATUSES);
        if (request.ownerUserId() != null) {
            requireUser(request.ownerUserId());
        }
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        if (request.id() == null) {
            jdbc.update("""
                    insert into platform_support_tickets
                        (id, tenant_id, subject, category, priority, status, description, owner_user_id, updated_by)
                    values (?,?,?,?,?,?,?,?,?)
                    """, id, tenantId, subject, category, priority, status, request.description(), request.ownerUserId(), userId);
            audit.record("PLATFORM_SUPPORT_TICKET_CREATE", "SUPPORT_TICKET", id,
                    Map.of("tenantId", tenantId.toString(), "status", status));
        } else {
            int updated = jdbc.update("""
                    update platform_support_tickets
                    set tenant_id=?, subject=?, category=?, priority=?, status=?, description=?,
                        owner_user_id=?, updated_at=now(), updated_by=?
                    where id=? and soft_deleted_at is null
                    """, tenantId, subject, category, priority, status, request.description(), request.ownerUserId(), userId, id);
            if (updated == 0) {
                throw new NotFoundException("SupportTicket", id);
            }
            audit.record("PLATFORM_SUPPORT_TICKET_UPDATE", "SUPPORT_TICKET", id,
                    Map.of("tenantId", tenantId.toString(), "status", status));
        }
        return findTicket(id);
    }

    @PutMapping("/support-tickets/{id}/status")
    @Transactional
    @PreAuthorize(SUPPORT)
    public SupportTicketDto updateSupportTicketStatus(@PathVariable UUID id,
                                                       @RequestBody SupportTicketStatusRequest request) {
        String status = normalize(request.status(), "status", TICKET_STATUSES);
        int updated = jdbc.update("""
                update platform_support_tickets
                set status=?, updated_at=now(), updated_by=?
                where id=? and soft_deleted_at is null
                """, status, TenantContext.requireUserId(), id);
        if (updated == 0) {
            throw new NotFoundException("SupportTicket", id);
        }
        audit.record("PLATFORM_SUPPORT_TICKET_STATUS", "SUPPORT_TICKET", id, Map.of("status", status));
        return findTicket(id);
    }

    @GetMapping("/onboarding")
    @PreAuthorize(SUPPORT)
    public List<OnboardingTenantDto> onboarding() {
        return jdbc.query("""
                select t.id tenant_id, t.name tenant_name, t.slug tenant_slug, t.status tenant_status, t.created_at,
                       coalesce((select count(*) from users u where u.tenant_id=t.id and u.is_deleted=false), 0) users,
                       coalesce((select count(*) from users u where u.tenant_id=t.id and u.is_deleted=false and u.status='ACTIVE'), 0) active_users,
                       p.name plan_name, s.status subscription_status,
                       coalesce((select count(*) from platform_support_tickets st
                                 where st.tenant_id=t.id and st.soft_deleted_at is null and st.status <> 'RESOLVED'), 0) open_tickets
                from tenants t
                left join tenant_subscriptions s on s.tenant_id=t.id
                left join platform_subscription_plans p on p.id=s.plan_id
                order by t.created_at desc, t.name asc
                """, this::onboardingTenantDto);
    }

    @GetMapping("/usage")
    @PreAuthorize(PLATFORM_READ)
    public UsageAnalyticsDto usage() {
        List<TenantUsageDto> rows = tenantUsage();
        long tenants = rows.size();
        long activeTenants = rows.stream().filter(row -> "ACTIVE".equals(row.tenantStatus())).count();
        long users = rows.stream().mapToLong(TenantUsageDto::users).sum();
        long activeUsers = rows.stream().mapToLong(TenantUsageDto::activeUsers).sum();
        long leads = rows.stream().mapToLong(TenantUsageDto::leads).sum();
        long opportunities = rows.stream().mapToLong(TenantUsageDto::opportunities).sum();
        long openTickets = rows.stream().mapToLong(TenantUsageDto::openTickets).sum();
        return new UsageAnalyticsDto(tenants, activeTenants, users, activeUsers, leads, opportunities, openTickets, rows);
    }

    @GetMapping("/global-analytics")
    @PreAuthorize(DASHBOARD)
    public GlobalAnalyticsDto globalAnalytics() {
        BigDecimal mrr = money("""
                select coalesce(sum(p.monthly_price), 0)
                from tenant_subscriptions s
                join platform_subscription_plans p on p.id=s.plan_id
                where s.status='ACTIVE'
                """);
        long active = scalar("""
                select count(*) from tenant_subscriptions where status='ACTIVE'
                """);
        long trial = scalar("""
                select count(*) from tenant_subscriptions where status='TRIAL'
                """);
        List<PlanBreakdownDto> planBreakdown = jdbc.query("""
                select p.name plan_name, count(s.tenant_id) tenants, coalesce(sum(p.monthly_price), 0) monthly_revenue
                from platform_subscription_plans p
                left join tenant_subscriptions s on s.plan_id=p.id and s.status <> 'CANCELLED'
                group by p.name, p.monthly_price
                order by monthly_revenue desc, p.name asc
                """, (rs, row) -> new PlanBreakdownDto(rs.getString("plan_name"), rs.getLong("tenants"),
                        rs.getBigDecimal("monthly_revenue")));
        List<GrowthBucketDto> growth = jdbc.query("""
                select to_char(date_trunc('month', created_at), 'YYYY-MM') as month_str, count(*) as tenant_count
                from tenants
                group by date_trunc('month', created_at)
                order by month_str asc
                """, (rs, row) -> new GrowthBucketDto(rs.getString("month_str"), rs.getLong("tenant_count")));
        return new GlobalAnalyticsDto(mrr, active, trial, planBreakdown, growth);
    }

    @GetMapping("/announcements")
    @PreAuthorize(PLATFORM_READ)
    public List<AnnouncementDto> announcements() {
        return jdbc.query("""
                select id, title, body, audience, status, send_at, sent_at, created_at, updated_at
                from platform_announcements
                where soft_deleted_at is null
                order by updated_at desc
                """, this::announcementDto);
    }

    @PostMapping("/announcements")
    @Transactional
    @PreAuthorize(SUPPORT)
    public AnnouncementDto saveAnnouncement(@RequestBody AnnouncementRequest request) {
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String title = requiredText(request.title(), "title");
        String body = requiredText(request.body(), "body");
        String audience = normalize(defaultText(request.audience(), "ALL_TENANTS"), "audience", ANNOUNCEMENT_AUDIENCES);
        String status = normalize(defaultText(request.status(), request.sendAt() == null ? "DRAFT" : "SCHEDULED"),
                "status", ANNOUNCEMENT_STATUSES);
        Instant sentAt = "SENT".equals(status) ? Instant.now() : null;
        if (request.id() == null) {
            jdbc.update("""
                    insert into platform_announcements
                        (id, title, body, audience, status, send_at, sent_at, updated_by)
                    values (?,?,?,?,?,?,?,?)
                    """, id, title, body, audience, status, request.sendAt(), sentAt, userId);
            audit.record("PLATFORM_ANNOUNCEMENT_CREATE", "ANNOUNCEMENT", id, Map.of("status", status));
        } else {
            int updated = jdbc.update("""
                    update platform_announcements
                    set title=?, body=?, audience=?, status=?, send_at=?, sent_at=coalesce(?, sent_at),
                        updated_at=now(), updated_by=?
                    where id=? and soft_deleted_at is null
                    """, title, body, audience, status, request.sendAt(), sentAt, userId, id);
            if (updated == 0) {
                throw new NotFoundException("Announcement", id);
            }
            audit.record("PLATFORM_ANNOUNCEMENT_UPDATE", "ANNOUNCEMENT", id, Map.of("status", status));
        }
        return findAnnouncement(id);
    }

    @PostMapping("/announcements/{id}/send")
    @Transactional
    @PreAuthorize(SUPPORT)
    public AnnouncementDto sendAnnouncement(@PathVariable UUID id) {
        int updated = jdbc.update("""
                update platform_announcements
                set status='SENT', sent_at=now(), updated_at=now(), updated_by=?
                where id=? and soft_deleted_at is null
                """, TenantContext.requireUserId(), id);
        if (updated == 0) {
            throw new NotFoundException("Announcement", id);
        }
        audit.record("PLATFORM_ANNOUNCEMENT_SEND", "ANNOUNCEMENT", id);
        return findAnnouncement(id);
    }

    @GetMapping("/knowledge-base")
    @PreAuthorize(PLATFORM_READ)
    public List<KnowledgeArticleDto> knowledgeBase() {
        return jdbc.query("""
                select id, title, category, summary, body, updated_at
                from platform_knowledge_articles
                where soft_deleted_at is null
                order by category asc, updated_at desc
                """, this::knowledgeArticleDto);
    }

    @PostMapping("/knowledge-base")
    @Transactional
    @PreAuthorize(SUPPORT)
    public KnowledgeArticleDto saveKnowledgeArticle(@RequestBody KnowledgeArticleRequest request) {
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String title = requiredText(request.title(), "title");
        String category = defaultText(request.category(), "Operations");
        String body = requiredText(request.body(), "body");
        if (request.id() == null) {
            jdbc.update("""
                    insert into platform_knowledge_articles
                        (id, title, category, summary, body, updated_by)
                    values (?,?,?,?,?,?)
                    """, id, title, category, request.summary(), body, userId);
            audit.record("PLATFORM_KNOWLEDGE_ARTICLE_CREATE", "KNOWLEDGE_ARTICLE", id,
                    Map.of("category", category));
        } else {
            int updated = jdbc.update("""
                    update platform_knowledge_articles
                    set title=?, category=?, summary=?, body=?, updated_at=now(), updated_by=?
                    where id=? and soft_deleted_at is null
                    """, title, category, request.summary(), body, userId, id);
            if (updated == 0) {
                throw new NotFoundException("KnowledgeArticle", id);
            }
            audit.record("PLATFORM_KNOWLEDGE_ARTICLE_UPDATE", "KNOWLEDGE_ARTICLE", id,
                    Map.of("category", category));
        }
        return findArticle(id);
    }

    private List<TenantUsageDto> tenantUsage() {
        return jdbc.query("""
                select t.id tenant_id, t.name tenant_name, t.slug tenant_slug, t.status tenant_status,
                       coalesce((select count(*) from users u where u.tenant_id=t.id and u.is_deleted=false), 0) users,
                       coalesce((select count(*) from users u where u.tenant_id=t.id and u.is_deleted=false and u.status='ACTIVE'), 0) active_users,
                       coalesce((select count(*) from crm_records r where r.tenant_id=t.id and r.module_key='leads' and r.soft_deleted_at is null), 0) leads,
                       coalesce((select count(*) from crm_records r where r.tenant_id=t.id and r.module_key='opportunities' and r.soft_deleted_at is null), 0) opportunities,
                       coalesce((select count(*) from crm_records r where r.tenant_id=t.id and r.module_key='opportunities' and r.status='OPEN' and r.soft_deleted_at is null), 0) open_deals,
                       coalesce((select count(*) from crm_records r where r.tenant_id=t.id and r.module_key='opportunities' and r.status='WON' and r.soft_deleted_at is null), 0) won_deals,
                       coalesce((select count(*) from quotes q where q.tenant_id=t.id and q.soft_deleted_at is null), 0) purchase_orders,
                       coalesce((select count(*) from platform_support_tickets st where st.tenant_id=t.id and st.soft_deleted_at is null and st.status <> 'RESOLVED'), 0) open_tickets,
                       p.name plan_name, s.status subscription_status, p.monthly_price, p.currency
                from tenants t
                left join tenant_subscriptions s on s.tenant_id=t.id
                left join platform_subscription_plans p on p.id=s.plan_id
                order by leads desc, opportunities desc, t.name asc
                """, this::tenantUsageDto);
    }

    private SupportTicketDto findTicket(UUID id) {
        return jdbc.query("""
                select st.id, st.tenant_id, t.name tenant_name, t.slug tenant_slug,
                       st.subject, st.category, st.priority, st.status, st.description,
                       st.owner_user_id,
                       coalesce(nullif(trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')), ''), u.email) owner_name,
                       st.created_at, st.updated_at
                from platform_support_tickets st
                join tenants t on t.id = st.tenant_id
                left join users u on u.id = st.owner_user_id
                where st.id=? and st.soft_deleted_at is null
                """, this::supportTicketDto, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("SupportTicket", id));
    }

    private AnnouncementDto findAnnouncement(UUID id) {
        return jdbc.query("""
                select id, title, body, audience, status, send_at, sent_at, created_at, updated_at
                from platform_announcements
                where id=? and soft_deleted_at is null
                """, this::announcementDto, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Announcement", id));
    }

    private KnowledgeArticleDto findArticle(UUID id) {
        return jdbc.query("""
                select id, title, category, summary, body, updated_at
                from platform_knowledge_articles
                where id=? and soft_deleted_at is null
                """, this::knowledgeArticleDto, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("KnowledgeArticle", id));
    }

    private UUID requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new BadRequestException("tenantId is required");
        }
        Integer found = jdbc.queryForObject("select count(*) from tenants where id=?", Integer.class, tenantId);
        if (found == null || found == 0) {
            throw new NotFoundException("Tenant", tenantId);
        }
        return tenantId;
    }

    private void requireUser(UUID userId) {
        Integer found = jdbc.queryForObject("select count(*) from users where id=? and is_deleted=false", Integer.class, userId);
        if (found == null || found == 0) {
            throw new NotFoundException("User", userId);
        }
    }

    private String normalize(String value, String label, Set<String> allowed) {
        String normalized = requiredText(value, label).trim().toUpperCase(Locale.ROOT);
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

    private long scalar(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal money(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private SupportTicketDto supportTicketDto(ResultSet rs, int row) throws SQLException {
        return new SupportTicketDto(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getString("tenant_slug"),
                rs.getString("subject"),
                rs.getString("category"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getString("description"),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("owner_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private OnboardingTenantDto onboardingTenantDto(ResultSet rs, int row) throws SQLException {
        long users = rs.getLong("users");
        long activeUsers = rs.getLong("active_users");
        long openTickets = rs.getLong("open_tickets");
        String tenantStatus = rs.getString("tenant_status");
        String subscriptionStatus = rs.getString("subscription_status");
        String stage;
        String nextAction;
        int score;
        if (!"ACTIVE".equals(tenantStatus)) {
            stage = "ACCOUNT_REVIEW";
            nextAction = "Confirm tenant status and contract details.";
            score = 35;
        } else if (users == 0) {
            stage = "INVITE_USERS";
            nextAction = "Invite tenant admins and first sellers.";
            score = 45;
        } else if (subscriptionStatus == null) {
            stage = "BILLING_SETUP";
            nextAction = "Assign a subscription plan.";
            score = 55;
        } else if (openTickets > 0) {
            stage = "SUPPORT_REVIEW";
            nextAction = "Resolve open onboarding/support tickets.";
            score = 70;
        } else if (activeUsers < users) {
            stage = "ADOPTION";
            nextAction = "Re-activate inactive users and validate login access.";
            score = 82;
        } else {
            stage = "LIVE";
            nextAction = "Move to health monitoring cadence.";
            score = 95;
        }
        return new OnboardingTenantDto(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getString("tenant_slug"),
                tenantStatus,
                rs.getTimestamp("created_at").toInstant(),
                users,
                activeUsers,
                rs.getString("plan_name"),
                subscriptionStatus,
                openTickets,
                score,
                stage,
                nextAction);
    }

    private TenantUsageDto tenantUsageDto(ResultSet rs, int row) throws SQLException {
        return new TenantUsageDto(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getString("tenant_slug"),
                rs.getString("tenant_status"),
                rs.getLong("users"),
                rs.getLong("active_users"),
                rs.getLong("leads"),
                rs.getLong("opportunities"),
                rs.getLong("open_deals"),
                rs.getLong("won_deals"),
                rs.getLong("purchase_orders"),
                rs.getLong("open_tickets"),
                rs.getString("plan_name"),
                rs.getString("subscription_status"),
                rs.getBigDecimal("monthly_price"),
                rs.getString("currency"));
    }

    private AnnouncementDto announcementDto(ResultSet rs, int row) throws SQLException {
        return new AnnouncementDto(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("audience"),
                rs.getString("status"),
                rs.getTimestamp("send_at") == null ? null : rs.getTimestamp("send_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private KnowledgeArticleDto knowledgeArticleDto(ResultSet rs, int row) throws SQLException {
        return new KnowledgeArticleDto(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("summary"),
                rs.getString("body"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
