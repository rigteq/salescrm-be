package com.salescms.service;
import com.salescms.repository.TenantModuleRepository;
import com.salescms.entity.TenantModule;
import com.salescms.dto.MetadataDtos;
import com.salescms.repository.CustomFieldRepository;
import com.salescms.entity.CustomField;
import com.salescms.repository.CrmRecordRepository;
import com.salescms.repository.CrmRecordCustomValueRepository;
import com.salescms.entity.CrmRecordCustomValue;
import com.salescms.entity.CrmRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.entity.Pipeline;
import com.salescms.repository.PipelineRepository;
import com.salescms.entity.Stage;
import com.salescms.repository.StageRepository;
import com.salescms.mapper.SalesCmsMapper;
import com.salescms.service.FinanceHandoffService;
import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.salescms.dto.MetadataDtos.RecordDto;
import static com.salescms.dto.MetadataDtos.RecordRequest;

@Service
public class DynamicRecordService {

    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "NUMBER", "CURRENCY", "DATE", "DATETIME", "DROPDOWN", "MULTI_SELECT",
            "CHECKBOX", "PHONE", "EMAIL", "URL", "FILE", "USER_PICKER", "TEAM_PICKER",
            "ADDRESS", "RATING", "LOOKUP");

    private final CrmRecordRepository records;
    private final CrmRecordCustomValueRepository values;
    private final TenantModuleRepository modules;
    private final CustomFieldRepository fields;
    private final PipelineRepository pipelines;
    private final StageRepository stages;
    private final AuditService audit;
    private final WorkflowAutomationService workflows;
    private final FinanceHandoffService financeHandoff;
    private final ObjectMapper objectMapper;
    private final SalesCmsMapper mapper;

    public DynamicRecordService(CrmRecordRepository records, CrmRecordCustomValueRepository values,
                                TenantModuleRepository modules, CustomFieldRepository fields,
                                PipelineRepository pipelines, StageRepository stages,
                                AuditService audit, WorkflowAutomationService workflows,
                                FinanceHandoffService financeHandoff,
                                ObjectMapper objectMapper, SalesCmsMapper mapper) {
        this.records = records;
        this.values = values;
        this.modules = modules;
        this.fields = fields;
        this.pipelines = pipelines;
        this.stages = stages;
        this.audit = audit;
        this.workflows = workflows;
        this.financeHandoff = financeHandoff;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<RecordDto> list(String moduleKey, String q, UUID ownerUserId, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        requireEnabledModule(tenantId, moduleKey);
        String normalizedQ = q == null ? "" : q.trim();
        Page<CrmRecord> page = records.search(tenantId, normalizeKey(moduleKey), ownerUserId, normalizedQ, pageable);
        Map<UUID, Map<String, Object>> customValues = customValueMaps(
                page.getContent().stream().map(CrmRecord::getId).toList());
        return page.map(record -> toDto(record, customValues.getOrDefault(record.getId(), Map.of())));
    }

    @Transactional(readOnly = true)
    public RecordDto get(UUID recordId) {
        return toDto(find(recordId));
    }

    @Transactional
    public RecordDto create(String moduleKey, RecordRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String key = normalizeKey(moduleKey);
        requireEnabledModule(tenantId, key);

        CrmRecord record = new CrmRecord(key, requiredText(request.title(), "title"));
        applyStandardFields(record, request);
        resolveDefaultPipelineAndStage(record);
        validateStage(tenantId, record.getPipelineId(), record.getStageId(), key);
        syncStageStatus(record);

        record = records.save(record);
        writeCustomValues(record, request.customValues(), true);
        workflows.enqueue(record.getTenantId(), record.getModuleKey(), record.getId(), "ON_CREATE",
                Map.of("title", record.getTitle()));
        audit.record("CREATE", "CRM_RECORD", record.getId(), Map.of("moduleKey", key));
        return toDto(record);
    }

    @Transactional
    public RecordDto update(UUID recordId, RecordRequest request) {
        CrmRecord record = find(recordId);
        if (request.title() != null) {
            record.setTitle(requiredText(request.title(), "title"));
        }
        applyStandardFields(record, request);
        validateStage(record.getTenantId(), record.getPipelineId(), record.getStageId(), record.getModuleKey());
        syncStageStatus(record);
        record = records.save(record);
        writeCustomValues(record, request.customValues(), false);
        workflows.enqueue(record.getTenantId(), record.getModuleKey(), record.getId(), "ON_UPDATE",
                Map.of("title", record.getTitle()));
        audit.record("UPDATE", "CRM_RECORD", record.getId(), Map.of("moduleKey", record.getModuleKey()));
        return toDto(record);
    }

    @Transactional
    public RecordDto move(UUID recordId, UUID stageId, String requestedStatus, String lostReason) {
        CrmRecord record = find(recordId);
        Stage targetStage = validateStage(record.getTenantId(), record.getPipelineId(), stageId, record.getModuleKey());
        UUID from = record.getStageId();
        record.setStageId(stageId);
        if (targetStage != null && targetStage.isWon()) {
            record.setStatus("WON");
        } else if (targetStage != null && targetStage.isLost()) {
            record.setStatus("LOST");
        } else if (requestedStatus != null && !requestedStatus.isBlank()) {
            record.setStatus(requestedStatus.trim().toUpperCase(Locale.ROOT));
        } else {
            syncStageStatus(record);
        }
        if (targetStage != null && targetStage.isLost()) {
            record.setLostReason(requiredLostReason(lostReason));
        } else {
            record.setLostReason(null);
        }
        boolean handoffToFinance = targetStage != null
                && targetStage.isWon()
                && "opportunities".equals(record.getModuleKey())
                && record.markFinanceHandoffPending();
        if (targetStage == null || !targetStage.isWon()) {
            record.clearFinanceHandoff();
        }
        records.save(record);
        workflows.enqueue(record.getTenantId(), record.getModuleKey(), record.getId(), "ON_STAGE_CHANGE",
                Map.of("fromStageId", String.valueOf(from), "toStageId", String.valueOf(stageId)));
        workflows.enqueue(record.getTenantId(), record.getModuleKey(), record.getId(), "ON_STATUS_CHANGE",
                Map.of("status", record.getStatus()));
        audit.record("STAGE_MOVE", "CRM_RECORD", record.getId(),
                moveAuditDetail(record, from, stageId, targetStage));
        if (handoffToFinance) {
            financeHandoff.handoffWonDeal(record);
        }
        return toDto(record);
    }

    @Transactional
    public RecordDto assign(UUID recordId, UUID ownerUserId) {
        CrmRecord record = find(recordId);
        record.setOwnerUserId(ownerUserId);
        records.save(record);
        workflows.enqueue(record.getTenantId(), record.getModuleKey(), record.getId(), "ON_ASSIGNMENT_CHANGE",
                Map.of("ownerUserId", String.valueOf(ownerUserId)));
        audit.record("ASSIGN", "CRM_RECORD", record.getId(),
                Map.of("moduleKey", record.getModuleKey(), "ownerUserId", String.valueOf(ownerUserId)));
        return toDto(record);
    }

    @Transactional
    public void delete(UUID recordId) {
        CrmRecord record = find(recordId);
        record.softDelete();
        records.save(record);
        audit.record("DELETE", "CRM_RECORD", recordId, Map.of("moduleKey", record.getModuleKey()));
    }

    @Transactional(readOnly = true)
    public CrmRecord find(UUID recordId) {
        return records.findByIdAndTenantIdAndSoftDeletedAtIsNull(recordId, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("CrmRecord", recordId));
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("moduleKey is required");
        }
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    public static String normalizeFieldType(String fieldType) {
        if (fieldType == null || fieldType.isBlank()) {
            throw new BadRequestException("fieldType is required");
        }
        String normalized = fieldType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!FIELD_TYPES.contains(normalized)) {
            throw new BadRequestException("Unsupported custom field type: " + fieldType);
        }
        return normalized;
    }

    private void requireEnabledModule(UUID tenantId, String moduleKey) {
        TenantModule module = modules.findByTenantIdAndModuleKey(tenantId, normalizeKey(moduleKey))
                .orElseThrow(() -> new NotFoundException("TenantModule", moduleKey));
        if (!module.isEnabled()) {
            throw new BadRequestException("Module is disabled: " + moduleKey);
        }
    }

    private void applyStandardFields(CrmRecord record, RecordRequest request) {
        if (request.status() != null) {
            record.setStatus(request.status().trim().toUpperCase(Locale.ROOT));
        }
        record.setSource(request.source());
        record.setPriority(request.priority());
        record.setAmount(request.amount());
        record.setCurrency(request.currency());
        if (request.pipelineId() != null) {
            record.setPipelineId(request.pipelineId());
        }
        if (request.stageId() != null) {
            record.setStageId(request.stageId());
        }
        if (request.ownerUserId() != null) {
            record.setOwnerUserId(request.ownerUserId());
        }
    }

    private void resolveDefaultPipelineAndStage(CrmRecord record) {
        UUID tenantId = TenantContext.requireTenantId();
        if (record.getPipelineId() == null) {
            pipelines.findFirstByTenantIdAndModuleKeyAndIsDefaultTrue(tenantId, record.getModuleKey())
                    .or(() -> pipelines.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
                            tenantId, record.getModuleKey()).stream().findFirst())
                    .map(Pipeline::getId)
                    .ifPresent(record::setPipelineId);
        }
        if (record.getStageId() == null && record.getPipelineId() != null) {
            stages.findByTenantIdAndPipelineIdOrderByPositionAsc(tenantId, record.getPipelineId()).stream()
                    .findFirst()
                    .map(Stage::getId)
                    .ifPresent(record::setStageId);
        }
    }

    private Stage validateStage(UUID tenantId, UUID pipelineId, UUID stageId, String moduleKey) {
        if (pipelineId == null && stageId == null) {
            return null;
        }
        if (pipelineId == null) {
            throw new BadRequestException("pipelineId is required when stageId is set");
        }
        Pipeline pipeline = pipelines.findById(pipelineId)
                .filter(p -> p.getTenantId().equals(tenantId) && p.getSoftDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Pipeline", pipelineId));
        if (!moduleKey.equals(pipeline.getModuleKey())) {
            throw new BadRequestException("Pipeline does not belong to module " + moduleKey);
        }
        if (stageId != null) {
            Stage stage = stages.findByIdAndTenantId(stageId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Stage", stageId));
            if (!stage.getPipelineId().equals(pipelineId)) {
                throw new BadRequestException("Stage does not belong to the selected pipeline");
            }
            return stage;
        }
        return null;
    }

    private void syncStageStatus(CrmRecord record) {
        if (record.getStageId() == null) {
            if (record.getStatus() == null || record.getStatus().isBlank()) {
                record.setStatus("OPEN");
            }
            return;
        }
        Stage stage = stages.findByIdAndTenantId(record.getStageId(), TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Stage", record.getStageId()));
        if (stage.isWon()) {
            record.setStatus("WON");
        } else if (stage.isLost()) {
            record.setStatus("LOST");
        } else if (record.getStatus() == null || record.getStatus().isBlank()
                || "WON".equals(record.getStatus()) || "LOST".equals(record.getStatus())) {
            record.setStatus("OPEN");
        }
    }

    private void writeCustomValues(CrmRecord record, Map<String, Object> requestValues, boolean creating) {
        UUID tenantId = record.getTenantId();
        List<CustomField> moduleFields = fields.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(
                tenantId, record.getModuleKey());
        Map<String, CustomField> byKey = moduleFields.stream()
                .collect(Collectors.toMap(CustomField::getFieldKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, Object> incoming = requestValues == null ? Map.of() : requestValues;
        Map<String, Object> existing = customValueMap(record.getId());

        for (CustomField field : moduleFields) {
            boolean hasIncoming = incoming.containsKey(field.getFieldKey());
            Object value = incoming.get(field.getFieldKey());
            if (field.isRequired()) {
                Object resolved = hasIncoming ? value : existing.get(field.getFieldKey());
                if (creating && !hasIncoming || isBlankValue(resolved)) {
                    throw new BadRequestException(field.getLabel() + " is required");
                }
            }
            if (hasIncoming) {
                writeValue(record, field, value);
            }
        }

        for (String fieldKey : incoming.keySet()) {
            if (!byKey.containsKey(fieldKey)) {
                throw new BadRequestException("Unknown custom field: " + fieldKey);
            }
        }
    }

    private void writeValue(CrmRecord record, CustomField field, Object raw) {
        CrmRecordCustomValue value = values.findByTenantIdAndRecordIdAndFieldKey(
                        record.getTenantId(), record.getId(), field.getFieldKey())
                .orElseGet(() -> new CrmRecordCustomValue(record.getTenantId(), record.getId(),
                        field.getId(), field.getFieldKey()));
        value.clearValues();
        if (raw == null || "".equals(raw)) {
            values.save(value);
            return;
        }
        switch (field.getFieldType()) {
            case "NUMBER", "CURRENCY", "RATING" -> value.setValueNumber(decimalValue(raw));
            case "CHECKBOX" -> value.setValueBoolean(booleanValue(raw));
            case "DATE" -> value.setValueDate(dateValue(raw));
            case "DATETIME" -> value.setValueDatetime(datetimeValue(raw));
            case "LOOKUP" -> value.setValueRecordId(lookupValue(record.getTenantId(), field, raw));
            case "MULTI_SELECT" -> value.setValueJson(jsonValue(raw));
            case "FILE" -> {
                if (raw instanceof String text) {
                    value.setFileUrl(text);
                } else {
                    value.setValueJson(jsonValue(raw));
                }
            }
            default -> value.setValueText(stringValue(raw));
        }
        values.save(value);
    }

    private Map<String, Object> customValueMap(UUID recordId) {
        return customValueMaps(List.of(recordId)).getOrDefault(recordId, Map.of());
    }

    private Map<UUID, Map<String, Object>> customValueMaps(List<UUID> recordIds) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, Map<String, Object>> result = new LinkedHashMap<>();
        if (recordIds == null || recordIds.isEmpty()) {
            return result;
        }
        for (CrmRecordCustomValue value : values.findByTenantIdAndRecordIdIn(tenantId, recordIds)) {
            Object resolved = valueObject(value);
            if (resolved != null) {
                result.computeIfAbsent(value.getRecordId(), ignored -> new LinkedHashMap<>())
                        .put(value.getFieldKey(), resolved);
            }
        }
        return result;
    }

    private Object valueObject(CrmRecordCustomValue value) {
        if (value.getValueRecordId() != null) return value.getValueRecordId().toString();
        if (value.getValueNumber() != null) return value.getValueNumber();
        if (value.getValueBoolean() != null) return value.getValueBoolean();
        if (value.getValueDate() != null) return value.getValueDate();
        if (value.getValueDatetime() != null) return value.getValueDatetime();
        if (value.getFileUrl() != null) return value.getFileUrl();
        if (value.getValueJson() != null) {
            try {
                return objectMapper.readValue(value.getValueJson(), Object.class);
            } catch (JsonProcessingException ignored) {
                return value.getValueJson();
            }
        }
        return value.getValueText();
    }

    @Transactional
    public RecordDto createFromForm(String moduleKey, UUID pipelineId, UUID stageId, Map<String, Object> payload) {
        Map<String, Object> values = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        String title = stringValue(values.remove("title"));
        RecordRequest request = new RecordRequest(
                title == null || title.isBlank() ? "Untitled record" : title,
                stringValue(values.remove("status")),
                stringValue(values.remove("source")),
                stringValue(values.remove("priority")),
                decimalValue(values.remove("amount")),
                stringValue(values.remove("currency")),
                pipelineId,
                stageId,
                null,
                values);
        return create(moduleKey, request);
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }

    private String requiredLostReason(String value) {
        String normalized = requiredText(value, "lostReason");
        if (normalized.length() > 1000) {
            throw new BadRequestException("lostReason must be 1000 characters or fewer");
        }
        return normalized;
    }

    private Map<String, Object> moveAuditDetail(CrmRecord record, UUID from, UUID to, Stage targetStage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleKey", record.getModuleKey());
        detail.put("from", String.valueOf(from));
        detail.put("to", String.valueOf(to));
        detail.put("status", record.getStatus());
        if (targetStage != null && targetStage.isLost()) {
            detail.put("lostReason", record.getLostReason());
        }
        return detail;
    }

    private boolean isBlankValue(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Expected numeric value");
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private LocalDate dateValue(Object value) {
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Expected ISO date value");
        }
    }

    private Instant datetimeValue(Object value) {
        String text = String.valueOf(value);
        try {
            return Instant.parse(text.endsWith("Z") || text.contains("+") ? text : text + "Z");
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Expected ISO datetime value");
        }
    }

    private String jsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid JSON value");
        }
    }

    /**
     * Resolves and validates a LOOKUP value: the raw input is the id of a record
     * in the field's target module. Returns null for a cleared reference.
     */
    private UUID lookupValue(UUID tenantId, CustomField field, Object raw) {
        UUID targetId;
        try {
            targetId = raw instanceof UUID id ? id : UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(field.getLabel() + " must reference a record id");
        }
        String targetModule = lookupModuleKey(field);
        CrmRecord target = records.findByIdAndTenantIdAndSoftDeletedAtIsNull(targetId, tenantId)
                .orElseThrow(() -> new BadRequestException(field.getLabel() + " references a record that does not exist"));
        if (targetModule != null && !targetModule.equals(target.getModuleKey())) {
            throw new BadRequestException(field.getLabel() + " must reference a " + targetModule + " record");
        }
        return targetId;
    }

    private String lookupModuleKey(CustomField field) {
        try {
            Map<String, Object> options = objectMapper.readValue(
                    field.getOptionsJson() == null || field.getOptionsJson().isBlank() ? "{}" : field.getOptionsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            Object key = options.get("lookupModuleKey");
            return key == null ? null : normalizeKey(String.valueOf(key));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * Reverse lookup: records in {@code childModuleKey} whose {@code fieldKey}
     * LOOKUP points at {@code recordId} (e.g. an account's contacts).
     */
    @Transactional(readOnly = true)
    public Page<RecordDto> related(UUID recordId, String childModuleKey, String fieldKey, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String key = normalizeKey(childModuleKey);
        requireEnabledModule(tenantId, key);
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new BadRequestException("fieldKey is required");
        }
        Page<CrmRecord> page = records.findByLookup(tenantId, key, fieldKey.trim(), recordId, pageable);
        Map<UUID, Map<String, Object>> customValues = customValueMaps(
                page.getContent().stream().map(CrmRecord::getId).toList());
        return page.map(record -> toDto(record, customValues.getOrDefault(record.getId(), Map.of())));
    }

    private RecordDto toDto(CrmRecord record) {
        return mapper.toRecordDto(record, customValueMap(record.getId()));
    }

    private RecordDto toDto(CrmRecord record, Map<String, Object> customValues) {
        return mapper.toRecordDto(record, customValues);
    }

}
