package com.salescms.crm.imports;

import com.salescms.metadata.CustomField;
import com.salescms.metadata.CustomFieldRepository;
import com.salescms.metadata.DynamicRecordService;
import com.salescms.metadata.MetadataDtos.RecordDto;
import com.salescms.metadata.MetadataDtos.RecordRequest;
import com.salescms.platform.audit.AuditService;
import com.salescms.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that bulk import routes every row through the single metadata write
 * path, isolates per-row failures, and only forwards known custom-field columns.
 * Concrete collaborators are hand-faked so the test does not depend on runtime
 * bytecode instrumentation.
 */
class ImportControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    /** Captures the (moduleKey, request) pairs the controller writes. */
    private static final class RecordingRecordService extends DynamicRecordService {
        final List<String> modules = new ArrayList<>();
        final List<RecordRequest> requests = new ArrayList<>();

        RecordingRecordService() {
            // 11 args: matches DynamicRecordService(records, values, modules, fields, pipelines,
            // stages, audit, workflows, financeHandoff, objectMapper, mapper).
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public RecordDto create(String moduleKey, RecordRequest request) {
            modules.add(moduleKey);
            requests.add(request);
            return null;
        }
    }

    private CustomFieldRepository fields;
    private List<CustomField> configuredFields = List.of();

    private RecordingRecordService records;
    private ImportController controller;

    @BeforeEach
    void setUp() {
        records = new RecordingRecordService();
        fields = fieldRepository();
        AuditService audit = new AuditService(null) {
            @Override
            public void record(String action, String objectType, UUID objectId, Map<String, Object> detail) {
                // no-op
            }
        };
        controller = new ImportController(records, fields, audit);
        TenantContext.set(TENANT, USER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void leadFields() {
        configuredFields = List.of(
                new CustomField(TENANT, "leads", "email", "Email", "EMAIL"),
                new CustomField(TENANT, "leads", "phone", "Phone", "PHONE"));
    }

    private CustomFieldRepository fieldRepository() {
        return (CustomFieldRepository) Proxy.newProxyInstance(
                CustomFieldRepository.class.getClassLoader(),
                new Class<?>[] { CustomFieldRepository.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return objectMethod(proxy, method, args);
                    }
                    if ("findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc"
                            .equals(method.getName())) {
                        UUID tenantId = (UUID) args[0];
                        String moduleKey = (String) args[1];
                        return TENANT.equals(tenantId) && "leads".equals(moduleKey) ? configuredFields : List.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "ImportControllerTest.CustomFieldRepository";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    @Test
    void importsValidRowsAndCollectsPerRowErrors() {
        leadFields();
        List<Map<String, String>> rows = List.of(
                Map.of("firstName", "Ada", "lastName", "Lovelace", "email", "ada@x.io"),
                Map.of("firstName", "NoLast"));

        var result = controller.importLeads(rows);

        assertThat(result.received()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(2);
            assertThat(e.message()).contains("lastName");
        });
        assertThat(records.modules).containsExactly("leads");
    }

    @Test
    void mapsNamePartsToTitleAndOnlyKnownCustomFields() {
        leadFields();
        List<Map<String, String>> rows = List.of(
                Map.of("firstName", "Ada", "lastName", "Lovelace", "email", "ada@x.io", "unmapped", "ignore-me"));

        controller.importLeads(rows);

        assertThat(records.requests).hasSize(1);
        RecordRequest sent = records.requests.get(0);
        assertThat(sent.title()).isEqualTo("Ada Lovelace");
        assertThat(sent.customValues()).containsEntry("email", "ada@x.io");
        assertThat(sent.customValues()).doesNotContainKey("unmapped");
        assertThat(sent.customValues()).doesNotContainKey("company_name");
    }
}
