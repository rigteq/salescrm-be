package com.salescms.analytics;

import com.salescms.platform.tenancy.TenantContext;
import com.salescms.platform.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only pipeline & lead analytics, aggregated in the database per tenant. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    public record StageBucket(String stage, long count, BigDecimal value) {
    }

    public record LeaderRow(String name, long openCount, BigDecimal openValue) {
    }

    public record ForecastBucket(String category, long count, BigDecimal value, BigDecimal weightedValue) {
    }

    public record OwnerRollup(UUID ownerUserId, String name, long openCount, BigDecimal openValue,
                              BigDecimal weightedValue, BigDecimal wonValue, long closedCount,
                              double winRate) {
    }

    public record Summary(
            String timeframe, LocalDate fromDate, LocalDate toDate,
            long openOpps, BigDecimal openValue, BigDecimal weightedValue,
            long wonCount, long lostCount, BigDecimal wonValue, double winRate,
            Map<String, Long> leadsByStatus,
            List<StageBucket> pipelineByStage,
            List<LeaderRow> leaderboard,
            List<ForecastBucket> forecastByCategory,
            List<OwnerRollup> ownerRollups) {
    }

    private record TimeWindow(String timeframe, LocalDate fromDate, LocalDate toDate, LocalDate toExclusive) {
        boolean all() {
            return fromDate == null;
        }
    }

    private final JdbcTemplate jdbc;

    public AnalyticsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionService.hasAnyPermission('REPORT_VIEW_COMPANY','REPORT_VIEW_TEAM','REPORT_VIEW_OWN','PLATFORM_DASHBOARD_VIEW')")
    public Summary summary(@RequestParam(defaultValue = "quarter") String timeframe) {
        UUID tenantId = TenantContext.requireTenantId();
        TimeWindow window = resolveWindow(timeframe);

        long openOpps = scalarLong(withOpps(window, "select count(*) from opps where status='OPEN'"),
                cteArgs(tenantId, window));
        BigDecimal openValue = scalarMoney(withOpps(window, "select coalesce(sum(amount),0) from opps where status='OPEN'"),
                cteArgs(tenantId, window));
        BigDecimal weighted = scalarMoney(withOpps(window,
                "select coalesce(sum(amount * coalesce(stage_prob,0) / 100.0),0) from opps where status='OPEN'"),
                cteArgs(tenantId, window));
        long wonCount = scalarLong(withOpps(window, "select count(*) from opps where status='WON'"),
                cteArgs(tenantId, window));
        long lostCount = scalarLong(withOpps(window, "select count(*) from opps where status='LOST'"),
                cteArgs(tenantId, window));
        BigDecimal wonValue = scalarMoney(withOpps(window, "select coalesce(sum(amount),0) from opps where status='WON'"),
                cteArgs(tenantId, window));
        long closed = wonCount + lostCount;
        double winRate = winRate(wonCount, closed);

        Map<String, Long> leadsByStatus = new LinkedHashMap<>();
        jdbc.query(
                "select status, count(*) c from crm_records "
                        + "where tenant_id=? and module_key='leads' and soft_deleted_at is null group by status",
                rs -> { leadsByStatus.put(rs.getString("status"), rs.getLong("c")); }, tenantId);

        List<StageBucket> pipeline = new ArrayList<>();
        List<Object> stageArgs = cteArgsList(tenantId, window);
        stageArgs.add(tenantId);
        jdbc.query(withOpps(window,
                        "select s.name, count(o.id) c, coalesce(sum(o.amount),0) v "
                                + "from stages s "
                                + "join pipelines p on p.id=s.pipeline_id and p.is_default=true "
                                + "left join opps o on o.stage_id=s.id and o.status='OPEN' "
                                + "where s.tenant_id=? and s.is_won=false and s.is_lost=false "
                                + "group by s.name, s.position order by s.position"),
                rs -> { pipeline.add(new StageBucket(rs.getString("name"), rs.getLong("c"), rs.getBigDecimal("v"))); },
                stageArgs.toArray());

        List<ForecastBucket> forecast = new ArrayList<>();
        jdbc.query(withOpps(window,
                        "select coalesce(forecast_category,'PIPELINE') category, count(*) c, coalesce(sum(amount),0) v, "
                                + "coalesce(sum(amount * coalesce(stage_prob,0) / 100.0),0) w "
                                + "from opps where status='OPEN' "
                                + "group by coalesce(forecast_category,'PIPELINE') "
                                + "order by case coalesce(forecast_category,'PIPELINE') "
                                + "when 'COMMIT' then 1 when 'BEST_CASE' then 2 when 'PIPELINE' then 3 "
                                + "when 'CLOSED' then 4 else 5 end"),
                rs -> {
                    forecast.add(new ForecastBucket(rs.getString("category"), rs.getLong("c"),
                            rs.getBigDecimal("v"), rs.getBigDecimal("w")));
                },
                cteArgs(tenantId, window));

        List<OwnerRollup> ownerRollups = new ArrayList<>();
        jdbc.query(withOpps(window,
                        "select o.owner_user_id, coalesce(u.first_name || ' ' || u.last_name, 'Unassigned') name, "
                                + "count(*) filter (where o.status='OPEN') open_count, "
                                + "coalesce(sum(o.amount) filter (where o.status='OPEN'),0) open_value, "
                                + "coalesce(sum(o.amount * coalesce(o.stage_prob,0) / 100.0) "
                                + "filter (where o.status='OPEN'),0) weighted_value, "
                                + "coalesce(sum(o.amount) filter (where o.status='WON'),0) won_value, "
                                + "count(*) filter (where o.status in ('WON','LOST')) closed_count, "
                                + "count(*) filter (where o.status='WON') won_count "
                                + "from opps o left join users u on u.id=o.owner_user_id "
                                + "group by o.owner_user_id, coalesce(u.first_name || ' ' || u.last_name, 'Unassigned') "
                                + "order by open_value desc, weighted_value desc, won_value desc, name"),
                rs -> {
                    long ownerWon = rs.getLong("won_count");
                    long ownerClosed = rs.getLong("closed_count");
                    ownerRollups.add(new OwnerRollup(
                            rs.getObject("owner_user_id", UUID.class),
                            rs.getString("name"),
                            rs.getLong("open_count"),
                            rs.getBigDecimal("open_value"),
                            rs.getBigDecimal("weighted_value"),
                            rs.getBigDecimal("won_value"),
                            ownerClosed,
                            winRate(ownerWon, ownerClosed)));
                },
                cteArgs(tenantId, window));

        List<LeaderRow> leaderboard = ownerRollups.stream()
                .limit(5)
                .map(r -> new LeaderRow(r.name(), r.openCount(), r.openValue()))
                .toList();

        return new Summary(window.timeframe(), window.fromDate(), window.toDate(),
                openOpps, openValue, weighted, wonCount, lostCount, wonValue, winRate,
                leadsByStatus, pipeline, leaderboard, forecast, ownerRollups);
    }

    /**
     * Wraps {@code tail} with the {@code opps} CTE: opportunities read from the
     * metadata model (crm_records), with stage probability and the close-date /
     * forecast-category custom fields projected as columns. Placeholders: tenant
     * id, then (unless "all") the close-date window [from, toExclusive).
     */
    private String withOpps(TimeWindow window, String tail) {
        String dateFilter = window.all() ? "" : " and cd.value_date >= ? and cd.value_date < ?";
        return "with opps as ("
                + "select r.id, r.status, r.amount, r.owner_user_id, r.stage_id, "
                + "s.probability stage_prob, cd.value_date close_date, fc.value_text forecast_category "
                + "from crm_records r "
                + "left join stages s on s.id=r.stage_id and s.tenant_id=r.tenant_id "
                + "left join crm_record_custom_values cd on cd.record_id=r.id and cd.field_key='close_date' "
                + "left join crm_record_custom_values fc on fc.record_id=r.id and fc.field_key='forecast_category' "
                + "where r.tenant_id=? and r.module_key='opportunities' and r.soft_deleted_at is null"
                + dateFilter
                + ") " + tail;
    }

    private TimeWindow resolveWindow(String timeframe) {
        String value = timeframe == null ? "quarter" : timeframe.trim().toLowerCase();
        LocalDate today = LocalDate.now();
        return switch (value) {
            case "month" -> {
                LocalDate from = today.withDayOfMonth(1);
                LocalDate toExclusive = from.plusMonths(1);
                yield new TimeWindow(value, from, toExclusive.minusDays(1), toExclusive);
            }
            case "quarter" -> {
                int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate from = LocalDate.of(today.getYear(), firstMonth, 1);
                LocalDate toExclusive = from.plusMonths(3);
                yield new TimeWindow(value, from, toExclusive.minusDays(1), toExclusive);
            }
            case "year" -> {
                LocalDate from = LocalDate.of(today.getYear(), 1, 1);
                LocalDate toExclusive = from.plusYears(1);
                yield new TimeWindow(value, from, toExclusive.minusDays(1), toExclusive);
            }
            case "all" -> new TimeWindow(value, null, null, null);
            default -> throw new BadRequestException("Invalid analytics timeframe: " + timeframe);
        };
    }

    private Object[] cteArgs(UUID tenantId, TimeWindow window) {
        return cteArgsList(tenantId, window).toArray();
    }

    private List<Object> cteArgsList(UUID tenantId, TimeWindow window) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (!window.all()) {
            args.add(window.fromDate());
            args.add(window.toExclusive());
        }
        return args;
    }

    private double winRate(long won, long closed) {
        return closed == 0 ? 0.0 : Math.round((won * 1000.0) / closed) / 10.0;
    }

    private long scalarLong(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private BigDecimal scalarMoney(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }
}
