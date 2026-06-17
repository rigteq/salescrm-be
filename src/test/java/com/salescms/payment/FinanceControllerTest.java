package com.salescms.payment;

import com.salescms.platform.audit.AuditService;
import com.salescms.platform.common.BadRequestException;
import com.salescms.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID RECORD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant HANDOFF_AT = Instant.parse("2026-06-01T10:15:30Z");

    private RecordingAudit audit;

    @BeforeEach
    void setUp() {
        audit = new RecordingAudit();
        TenantContext.set(TENANT, USER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void advancesSequentiallyAndPreservesOriginalHandoffTime() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate("AWAITING_INVOICE");
        FinanceController controller = new FinanceController(jdbc, audit);

        FinanceController.FinanceHandoffDto result = controller.advance(
                RECORD, new FinanceController.AdvanceHandoffRequest("invoice-drafted"));

        assertThat(result.status()).isEqualTo("INVOICE_DRAFTED");
        assertThat(result.handoffAt()).isEqualTo(HANDOFF_AT);
        assertThat(jdbc.updateSql).contains("updated_by=?");
        assertThat(jdbc.updateSql).doesNotContain("finance_handoff_at=now()");
        assertThat(jdbc.updateArgs).containsExactly(
                "INVOICE_DRAFTED", USER, RECORD, TENANT, "AWAITING_INVOICE");
        assertThat(audit.action).isEqualTo("FINANCE_HANDOFF_UPDATE");
        assertThat(audit.objectType).isEqualTo("CRM_RECORD");
        assertThat(audit.objectId).isEqualTo(RECORD);
        assertThat(audit.detail).containsEntry("from", "AWAITING_INVOICE")
                .containsEntry("to", "INVOICE_DRAFTED");
    }

    @Test
    void rejectsSkippedTransitionsWithoutWritingOrAuditing() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate("AWAITING_INVOICE");
        FinanceController controller = new FinanceController(jdbc, audit);

        assertThatThrownBy(() -> controller.advance(
                RECORD, new FinanceController.AdvanceHandoffRequest("COMPLETED")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("AWAITING_INVOICE")
                .hasMessageContaining("INVOICE_DRAFTED");

        assertThat(jdbc.updates).isZero();
        assertThat(audit.action).isNull();
    }

    @Test
    void rejectsCompletedHandoffsWithoutWritingOrAuditing() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate("COMPLETED");
        FinanceController controller = new FinanceController(jdbc, audit);

        assertThatThrownBy(() -> controller.advance(
                RECORD, new FinanceController.AdvanceHandoffRequest("INVOICE_DRAFTED")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already completed");

        assertThat(jdbc.updates).isZero();
        assertThat(audit.action).isNull();
    }

    @Test
    void requiresAStatusBeforeReadingOrWriting() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate("AWAITING_INVOICE");
        FinanceController controller = new FinanceController(jdbc, audit);

        assertThatThrownBy(() -> controller.advance(RECORD, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status is required");

        assertThat(jdbc.queryArgs).isEmpty();
        assertThat(jdbc.updates).isZero();
    }

    @Test
    void tenantWideQueueDoesNotGrantOwnOnlyPoView() throws NoSuchMethodException {
        Method handoffs = FinanceController.class.getMethod("handoffs", String.class);
        Method summary = FinanceController.class.getMethod("summary");

        assertThat(handoffs.getAnnotation(PreAuthorize.class).value())
                .contains("PO_VIEW_ALL")
                .doesNotContain("PO_VIEW_OWN");
        assertThat(summary.getAnnotation(PreAuthorize.class).value())
                .contains("PO_VIEW_ALL")
                .doesNotContain("PO_VIEW_OWN");
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {
        final List<Object[]> queryArgs = new ArrayList<>();
        final Map<String, Object> row = new LinkedHashMap<>();
        String updateSql;
        Object[] updateArgs;
        int updates;

        FakeJdbcTemplate(String status) {
            row.put("id", RECORD);
            row.put("title", "Expansion deal");
            row.put("amount", new BigDecimal("42000.00"));
            row.put("currency", "INR");
            row.put("owner_user_id", OWNER);
            row.put("owner_name", "Finance Owner");
            row.put("finance_handoff_status", status);
            row.put("finance_handoff_at", HANDOFF_AT);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryArgs.add(args);
            try {
                if (sql.contains("select r.finance_handoff_status")) {
                    return List.of(rowMapper.mapRow(resultSet(Map.of(
                            "finance_handoff_status", row.get("finance_handoff_status"))), 0));
                }
                if (sql.contains("select r.id, r.title")) {
                    return List.of(rowMapper.mapRow(resultSet(row), 0));
                }
            } catch (SQLException e) {
                throw new DataRetrievalFailureException("Could not map fake finance row", e);
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates++;
            updateSql = sql;
            updateArgs = args;
            row.put("finance_handoff_status", args[0]);
            return 1;
        }
    }

    private static ResultSet resultSet(Map<String, Object> values) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return objectMethod(proxy, method, args);
                    }
                    return switch (method.getName()) {
                        case "getString" -> stringValue(values.get((String) args[0]));
                        case "getBigDecimal", "getObject" -> values.get((String) args[0]);
                        case "getTimestamp" -> timestampValue(values.get((String) args[0]));
                        case "wasNull" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "FinanceControllerTest.ResultSet";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Timestamp timestampValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    private static final class RecordingAudit extends AuditService {
        String action;
        String objectType;
        UUID objectId;
        Map<String, Object> detail;

        RecordingAudit() {
            super(null);
        }

        @Override
        public void record(String action, String objectType, UUID objectId, Map<String, Object> detail) {
            this.action = action;
            this.objectType = objectType;
            this.objectId = objectId;
            this.detail = detail;
        }
    }
}
