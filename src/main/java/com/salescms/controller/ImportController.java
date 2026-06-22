package com.salescms.controller;

import com.salescms.entity.CustomField;
import com.salescms.repository.CustomFieldRepository;
import com.salescms.service.DynamicRecordService;
import com.salescms.dto.MetadataDtos.RecordRequest;
import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.entity.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk import: the client parses CSV/XLSX and maps columns, then POSTs an array
 * of row objects. Every row is created through the single metadata write path
 * ({@link DynamicRecordService}) so imported records are first-class CRM records.
 * Each row is its own transaction, so one bad row never blocks the rest; the
 * response reports per-row outcomes.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Set<String> LEAD_STATUSES =
            Set.of("NEW", "WORKING", "QUALIFIED", "UNQUALIFIED");

    public record RowError(int row, String message) {
    }

    public record ImportResult(int received, int created, int failed, List<RowError> errors) {
    }

    private final DynamicRecordService records;
    private final CustomFieldRepository fields;
    private final AuditService audit;

    public ImportController(DynamicRecordService records, CustomFieldRepository fields, AuditService audit) {
        this.records = records;
        this.fields = fields;
        this.audit = audit;
    }

    @PostMapping("/leads")
    @PreAuthorize("@permissionService.hasPermission('LEAD_IMPORT')")
    public ImportResult importLeads(@RequestBody List<Map<String, String>> rows) {
        return runImport("leads", rows, (row, fieldKeys) -> {
            String lastName = str(row, "lastName");
            if (lastName == null) {
                throw new BadRequestException("lastName is required");
            }
            String status = upper(str(row, "status"));
            return new RecordRequest(
                    name(str(row, "firstName"), lastName),
                    status != null && LEAD_STATUSES.contains(status) ? status : null,
                    str(row, "source"), null, null, null, null, null, null,
                    customValues(row, fieldKeys, "email", "phone", "company_name"));
        });
    }

    @PostMapping("/accounts")
    @PreAuthorize("@permissionService.hasPermission('LEAD_IMPORT')")
    public ImportResult importAccounts(@RequestBody List<Map<String, String>> rows) {
        return runImport("accounts", rows, (row, fieldKeys) -> {
            String accountName = str(row, "name");
            if (accountName == null) {
                throw new BadRequestException("name is required");
            }
            return new RecordRequest(
                    accountName, null, null, null, null, null, null, null, null,
                    customValues(row, fieldKeys, "industry", "website", "phone", "type", "billing_country"));
        });
    }

    @PostMapping("/contacts")
    @PreAuthorize("@permissionService.hasPermission('LEAD_IMPORT')")
    public ImportResult importContacts(@RequestBody List<Map<String, String>> rows) {
        return runImport("contacts", rows, (row, fieldKeys) -> {
            String lastName = str(row, "lastName");
            if (lastName == null) {
                throw new BadRequestException("lastName is required");
            }
            return new RecordRequest(
                    name(str(row, "firstName"), lastName), null, null, null, null, null, null, null, null,
                    customValues(row, fieldKeys, "email", "phone", "title"));
        });
    }

    private ImportResult runImport(String moduleKey, List<Map<String, String>> rows,
                                   RowMapper mapper) {
        Set<String> fieldKeys = fields
                .findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(
                        TenantContext.requireTenantId(), moduleKey)
                .stream().map(CustomField::getFieldKey).collect(Collectors.toSet());
        List<RowError> errors = new ArrayList<>();
        int created = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                records.create(moduleKey, mapper.map(rows.get(i), fieldKeys));
                created++;
            } catch (RuntimeException ex) {
                String message = ex.getMessage() != null ? ex.getMessage() : "Row could not be imported";
                errors.add(new RowError(i + 1, message));
            }
        }
        if (created > 0) {
            audit.record("IMPORT", moduleKey.toUpperCase(), null, Map.of("created", created));
        }
        return new ImportResult(rows.size(), created, errors.size(), errors);
    }

    /** Builds the custom-value map from the requested keys, ignoring any that the module doesn't define. */
    private Map<String, Object> customValues(Map<String, String> row, Set<String> fieldKeys, String... keys) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : keys) {
            String value = str(row, key);
            if (value != null && fieldKeys.contains(key)) {
                values.put(key, value);
            }
        }
        return values;
    }

    @FunctionalInterface
    private interface RowMapper {
        RecordRequest map(Map<String, String> row, Set<String> fieldKeys);
    }

    private static String name(String firstName, String lastName) {
        return firstName == null ? lastName : (firstName + " " + lastName).trim();
    }

    private static String str(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase();
    }
}
