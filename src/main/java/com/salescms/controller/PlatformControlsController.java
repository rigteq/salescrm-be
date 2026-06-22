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
public class PlatformControlsController {

    private static final String BILLING = "@permissionService.hasPermission('PLATFORM_BILLING_MANAGE')";
    private static final String FLAG_READ = "@permissionService.hasAnyPermission('PLATFORM_DASHBOARD_VIEW','PLATFORM_FEATURE_FLAG_MANAGE')";
    private static final String FLAG_MANAGE = "@permissionService.hasPermission('PLATFORM_FEATURE_FLAG_MANAGE')";
    private static final Set<String> SUBSCRIPTION_STATUSES = Set.of("TRIAL", "ACTIVE", "PAST_DUE", "CANCELLED");

    public record SubscriptionPlanDto(UUID id, String code, String name, String description,
                                      BigDecimal monthlyPrice, String currency, Integer seatLimit,
                                      Integer featureLimit, boolean active, Instant updatedAt) {
    }

    public record SubscriptionPlanRequest(UUID id, String code, String name, String description,
                                          BigDecimal monthlyPrice, String currency, Integer seatLimit,
                                          Integer featureLimit, Boolean active) {
    }

    public record TenantSubscriptionDto(UUID tenantId, String tenantName, String tenantSlug,
                                        String tenantStatus, UUID planId, String planCode, String planName,
                                        String status, int seatsPurchased, Instant trialEndsAt,
                                        Instant currentPeriodEndsAt, BigDecimal monthlyPrice,
                                        String currency, Instant updatedAt) {
    }

    public record TenantSubscriptionRequest(UUID planId, String status, Integer seatsPurchased,
                                            Instant trialEndsAt, Instant currentPeriodEndsAt) {
    }

    public record FeatureFlagDto(UUID id, String flagKey, String name, String description,
                                 boolean enabledByDefault, boolean active, int enabledTenantCount,
                                 Instant updatedAt) {
    }

    public record FeatureFlagRequest(UUID id, String flagKey, String name, String description,
                                     Boolean enabledByDefault, Boolean active) {
    }

    public record TenantFeatureFlagDto(UUID tenantId, String tenantName, String tenantSlug,
                                       UUID flagId, String flagKey, boolean effectiveEnabled,
                                       Boolean overrideEnabled) {
    }

    public record TenantFeatureFlagRequest(Boolean enabled) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PlatformControlsController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/subscription-plans")
    @PreAuthorize(BILLING)
    public List<SubscriptionPlanDto> subscriptionPlans() {
        return jdbc.query("""
                select id, code, name, description, monthly_price, currency, seat_limit,
                       feature_limit, active, updated_at
                from platform_subscription_plans
                order by monthly_price asc, code asc
                """, this::planDto);
    }

    @PostMapping("/subscription-plans")
    @Transactional
    @PreAuthorize(BILLING)
    public SubscriptionPlanDto saveSubscriptionPlan(@RequestBody SubscriptionPlanRequest request) {
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String code = normalizePlanCode(request.code());
        String name = requiredText(request.name(), "name");
        BigDecimal price = request.monthlyPrice() == null ? BigDecimal.ZERO : request.monthlyPrice();
        String currency = request.currency() == null || request.currency().isBlank()
                ? "USD" : request.currency().trim().toUpperCase(Locale.ROOT);
        boolean active = request.active() == null || request.active();
        if (request.id() == null) {
            jdbc.update("""
                    insert into platform_subscription_plans
                        (id, code, name, description, monthly_price, currency, seat_limit,
                         feature_limit, active, updated_by)
                    values (?,?,?,?,?,?,?,?,?,?)
                    """, id, code, name, request.description(), price, currency, request.seatLimit(),
                    request.featureLimit(), active, userId);
            audit.record("PLATFORM_SUBSCRIPTION_PLAN_CREATE", "SUBSCRIPTION_PLAN", id,
                    Map.of("code", code));
        } else {
            int updated = jdbc.update("""
                    update platform_subscription_plans
                    set code=?, name=?, description=?, monthly_price=?, currency=?, seat_limit=?,
                        feature_limit=?, active=?, updated_at=now(), updated_by=?
                    where id=?
                    """, code, name, request.description(), price, currency, request.seatLimit(),
                    request.featureLimit(), active, userId, id);
            if (updated == 0) {
                throw new NotFoundException("SubscriptionPlan", id);
            }
            audit.record("PLATFORM_SUBSCRIPTION_PLAN_UPDATE", "SUBSCRIPTION_PLAN", id,
                    Map.of("code", code));
        }
        return findPlan(id);
    }

    @GetMapping("/tenant-subscriptions")
    @PreAuthorize(BILLING)
    public List<TenantSubscriptionDto> tenantSubscriptions() {
        return jdbc.query("""
                select t.id tenant_id, t.name tenant_name, t.slug tenant_slug, t.status tenant_status,
                       p.id plan_id, p.code plan_code, p.name plan_name,
                       coalesce(s.status, 'TRIAL') status,
                       coalesce(s.seats_purchased, 1) seats_purchased,
                       s.trial_ends_at, s.current_period_ends_at,
                       p.monthly_price, p.currency, s.updated_at
                from tenants t
                left join tenant_subscriptions s on s.tenant_id = t.id
                left join platform_subscription_plans p on p.id = s.plan_id
                order by t.name asc
                """, this::tenantSubscriptionDto);
    }

    @PutMapping("/tenant-subscriptions/{tenantId}")
    @Transactional
    @PreAuthorize(BILLING)
    public TenantSubscriptionDto updateTenantSubscription(@PathVariable UUID tenantId,
                                                          @RequestBody TenantSubscriptionRequest request) {
        requireTenant(tenantId);
        requirePlan(request.planId());
        String status = normalizeSubscriptionStatus(request.status());
        int seats = request.seatsPurchased() == null || request.seatsPurchased() < 1 ? 1 : request.seatsPurchased();
        UUID userId = TenantContext.requireUserId();
        jdbc.update("""
                insert into tenant_subscriptions
                    (tenant_id, plan_id, status, seats_purchased, trial_ends_at,
                     current_period_ends_at, updated_by)
                values (?,?,?,?,?,?,?)
                on conflict (tenant_id) do update set
                    plan_id=excluded.plan_id,
                    status=excluded.status,
                    seats_purchased=excluded.seats_purchased,
                    trial_ends_at=excluded.trial_ends_at,
                    current_period_ends_at=excluded.current_period_ends_at,
                    updated_at=now(),
                    updated_by=excluded.updated_by
                """, tenantId, request.planId(), status, seats, request.trialEndsAt(),
                request.currentPeriodEndsAt(), userId);
        audit.record("PLATFORM_SUBSCRIPTION_UPDATE", "TENANT", tenantId,
                Map.of("planId", String.valueOf(request.planId()), "status", status));
        return tenantSubscriptions().stream()
                .filter(row -> row.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("TenantSubscription", tenantId));
    }

    @GetMapping("/feature-flags")
    @PreAuthorize(FLAG_READ)
    public List<FeatureFlagDto> featureFlags() {
        return jdbc.query("""
                select f.id, f.flag_key, f.name, f.description, f.enabled_by_default, f.active,
                       count(o.id) filter (where o.enabled = true) enabled_tenant_count,
                       f.updated_at
                from platform_feature_flags f
                left join tenant_feature_flags o on o.flag_id = f.id
                group by f.id
                order by f.flag_key asc
                """, this::featureFlagDto);
    }

    @PostMapping("/feature-flags")
    @Transactional
    @PreAuthorize(FLAG_MANAGE)
    public FeatureFlagDto saveFeatureFlag(@RequestBody FeatureFlagRequest request) {
        UUID userId = TenantContext.requireUserId();
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        String key = normalizeFlagKey(request.flagKey());
        String name = requiredText(request.name(), "name");
        boolean defaultEnabled = request.enabledByDefault() != null && request.enabledByDefault();
        boolean active = request.active() == null || request.active();
        if (request.id() == null) {
            jdbc.update("""
                    insert into platform_feature_flags
                        (id, flag_key, name, description, enabled_by_default, active, updated_by)
                    values (?,?,?,?,?,?,?)
                    """, id, key, name, request.description(), defaultEnabled, active, userId);
            audit.record("PLATFORM_FEATURE_FLAG_CREATE", "FEATURE_FLAG", id, Map.of("flagKey", key));
        } else {
            int updated = jdbc.update("""
                    update platform_feature_flags
                    set flag_key=?, name=?, description=?, enabled_by_default=?, active=?,
                        updated_at=now(), updated_by=?
                    where id=?
                    """, key, name, request.description(), defaultEnabled, active, userId, id);
            if (updated == 0) {
                throw new NotFoundException("FeatureFlag", id);
            }
            audit.record("PLATFORM_FEATURE_FLAG_UPDATE", "FEATURE_FLAG", id, Map.of("flagKey", key));
        }
        return findFlag(id);
    }

    @GetMapping("/feature-flags/tenants")
    @PreAuthorize(FLAG_READ)
    public List<TenantFeatureFlagDto> tenantFeatureFlags(@RequestParam(required = false) UUID flagId) {
        String filter = flagId == null ? "" : " where f.id=? ";
        Object[] args = flagId == null ? new Object[]{} : new Object[]{flagId};
        return jdbc.query("""
                select t.id tenant_id, t.name tenant_name, t.slug tenant_slug,
                       f.id flag_id, f.flag_key,
                       coalesce(o.enabled, f.enabled_by_default) effective_enabled,
                       o.enabled override_enabled
                from tenants t
                cross join platform_feature_flags f
                left join tenant_feature_flags o on o.tenant_id = t.id and o.flag_id = f.id
                """ + filter + """
                order by t.name asc, f.flag_key asc
                """, this::tenantFeatureFlagDto, args);
    }

    @PutMapping("/feature-flags/{flagId}/tenants/{tenantId}")
    @Transactional
    @PreAuthorize(FLAG_MANAGE)
    public TenantFeatureFlagDto updateTenantFeatureFlag(@PathVariable UUID flagId, @PathVariable UUID tenantId,
                                                        @RequestBody TenantFeatureFlagRequest request) {
        requireTenant(tenantId);
        requireFlag(flagId);
        if (request.enabled() == null) {
            throw new BadRequestException("enabled is required");
        }
        UUID userId = TenantContext.requireUserId();
        jdbc.update("""
                insert into tenant_feature_flags (tenant_id, flag_id, enabled, updated_by)
                values (?,?,?,?)
                on conflict (tenant_id, flag_id) do update set
                    enabled=excluded.enabled,
                    updated_at=now(),
                    updated_by=excluded.updated_by
                """, tenantId, flagId, request.enabled(), userId);
        audit.record("PLATFORM_FEATURE_FLAG_TENANT_UPDATE", "TENANT", tenantId,
                Map.of("flagId", String.valueOf(flagId), "enabled", request.enabled()));
        return tenantFeatureFlags(flagId).stream()
                .filter(row -> row.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("TenantFeatureFlag", tenantId));
    }

    private SubscriptionPlanDto findPlan(UUID id) {
        return jdbc.query("""
                select id, code, name, description, monthly_price, currency, seat_limit,
                       feature_limit, active, updated_at
                from platform_subscription_plans
                where id=?
                """, this::planDto, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("SubscriptionPlan", id));
    }

    private FeatureFlagDto findFlag(UUID id) {
        return jdbc.query("""
                select f.id, f.flag_key, f.name, f.description, f.enabled_by_default, f.active,
                       count(o.id) filter (where o.enabled = true) enabled_tenant_count,
                       f.updated_at
                from platform_feature_flags f
                left join tenant_feature_flags o on o.flag_id = f.id
                where f.id=?
                group by f.id
                """, this::featureFlagDto, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("FeatureFlag", id));
    }

    private void requireTenant(UUID tenantId) {
        Integer found = jdbc.queryForObject("select count(*) from tenants where id=?", Integer.class, tenantId);
        if (found == null || found == 0) {
            throw new NotFoundException("Tenant", tenantId);
        }
    }

    private void requirePlan(UUID planId) {
        if (planId == null) {
            throw new BadRequestException("planId is required");
        }
        Integer found = jdbc.queryForObject("select count(*) from platform_subscription_plans where id=?", Integer.class, planId);
        if (found == null || found == 0) {
            throw new NotFoundException("SubscriptionPlan", planId);
        }
    }

    private void requireFlag(UUID flagId) {
        Integer found = jdbc.queryForObject("select count(*) from platform_feature_flags where id=?", Integer.class, flagId);
        if (found == null || found == 0) {
            throw new NotFoundException("FeatureFlag", flagId);
        }
    }

    private String normalizeSubscriptionStatus(String value) {
        String status = value == null || value.isBlank() ? "TRIAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!SUBSCRIPTION_STATUSES.contains(status)) {
            throw new BadRequestException("Unsupported subscription status: " + value);
        }
        return status;
    }

    private String normalizePlanCode(String value) {
        return requiredText(value, "code").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private String normalizeFlagKey(String value) {
        return requiredText(value, "flagKey").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private SubscriptionPlanDto planDto(ResultSet rs, int row) throws SQLException {
        return new SubscriptionPlanDto(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("monthly_price"),
                rs.getString("currency"),
                (Integer) rs.getObject("seat_limit"),
                (Integer) rs.getObject("feature_limit"),
                rs.getBoolean("active"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private TenantSubscriptionDto tenantSubscriptionDto(ResultSet rs, int row) throws SQLException {
        return new TenantSubscriptionDto(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getString("tenant_slug"),
                rs.getString("tenant_status"),
                rs.getObject("plan_id", UUID.class),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getString("status"),
                rs.getInt("seats_purchased"),
                rs.getTimestamp("trial_ends_at") == null ? null : rs.getTimestamp("trial_ends_at").toInstant(),
                rs.getTimestamp("current_period_ends_at") == null ? null : rs.getTimestamp("current_period_ends_at").toInstant(),
                rs.getBigDecimal("monthly_price"),
                rs.getString("currency"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
    }

    private FeatureFlagDto featureFlagDto(ResultSet rs, int row) throws SQLException {
        return new FeatureFlagDto(
                rs.getObject("id", UUID.class),
                rs.getString("flag_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("enabled_by_default"),
                rs.getBoolean("active"),
                rs.getInt("enabled_tenant_count"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private TenantFeatureFlagDto tenantFeatureFlagDto(ResultSet rs, int row) throws SQLException {
        return new TenantFeatureFlagDto(
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getString("tenant_slug"),
                rs.getObject("flag_id", UUID.class),
                rs.getString("flag_key"),
                rs.getBoolean("effective_enabled"),
                (Boolean) rs.getObject("override_enabled"));
    }
}
