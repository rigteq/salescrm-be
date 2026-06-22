package com.salescms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.exception.BadRequestException;
import com.salescms.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowAutomationService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final NotificationService notifications;

    public WorkflowAutomationService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                     NotificationService notifications) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.notifications = notifications;
    }

    public void enqueue(UUID tenantId, String moduleKey, UUID recordId, String triggerType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid workflow payload");
        }
        List<WorkflowRuleRuntime> rules = jdbc.query("""
                select id, actions_json from workflow_rules
                where tenant_id=? and module_key=? and trigger_type=? and active=true and soft_deleted_at is null
                """, (rs, rowNum) -> new WorkflowRuleRuntime(
                rs.getObject("id", UUID.class), rs.getString("actions_json")),
                tenantId, moduleKey, triggerType);
        for (WorkflowRuleRuntime rule : rules) {
            UUID jobId = jdbc.queryForObject("""
                    insert into workflow_jobs (tenant_id, workflow_rule_id, record_id, status, payload_json)
                    values (?,?,?,'PENDING',?) returning id
                    """, UUID.class, tenantId, rule.id(), recordId, payloadJson);
            try {
                executeActions(tenantId, moduleKey, recordId, rule.actionsJson());
                jdbc.update("update workflow_jobs set status='COMPLETED', updated_at=now() where id=?", jobId);
            } catch (RuntimeException ex) {
                jdbc.update("update workflow_jobs set status='FAILED', updated_at=now() where id=?", jobId);
                throw ex;
            }
        }
    }

    private void executeActions(UUID tenantId, String moduleKey, UUID recordId, String actionsJson) {
        List<Map<String, Object>> actions = parseActions(actionsJson);
        for (Map<String, Object> action : actions) {
            String type = String.valueOf(action.getOrDefault("type", action.getOrDefault("action", "")))
                    .trim().toLowerCase().replace('-', '_');
            switch (type) {
                case "assign_user" -> assignUser(tenantId, recordId, uuid(action.get("userId"), action.get("ownerUserId")));
                case "assign_team" -> addTag(tenantId, recordId, "team:" + string(action.get("teamId")));
                case "create_task" -> createTask(tenantId, recordId, action);
                case "send_notification" -> sendNotification(tenantId, moduleKey, recordId, action);
                case "update_field" -> updateField(tenantId, moduleKey, recordId, action);
                case "change_stage" -> changeStage(tenantId, recordId, uuid(action.get("stageId")));
                case "add_tag" -> addTag(tenantId, recordId, string(action.getOrDefault("tag", "workflow")));
                case "escalate_to_manager" -> {
                    addTag(tenantId, recordId, "escalated");
                    sendNotification(tenantId, moduleKey, recordId, action);
                }
                case "" -> {
                    // Empty action arrays are valid placeholders for templates.
                }
                default -> throw new BadRequestException("Unsupported workflow action: " + type);
            }
        }
    }

    private List<Map<String, Object>> parseActions(String actionsJson) {
        try {
            return objectMapper.readValue(actionsJson == null || actionsJson.isBlank() ? "[]" : actionsJson,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid workflow actions JSON");
        }
    }

    private void assignUser(UUID tenantId, UUID recordId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        jdbc.update("update crm_records set owner_user_id=?, updated_at=now() where id=? and tenant_id=?",
                ownerUserId, recordId, tenantId);
    }

    private void changeStage(UUID tenantId, UUID recordId, UUID stageId) {
        if (stageId == null) {
            return;
        }
        jdbc.update("""
                update crm_records
                set stage_id=?, status=coalesce((
                    select case when outcome_type='WON' then 'WON'
                                when outcome_type='LOST' then 'LOST'
                                else 'OPEN' end
                    from stages where id=? and tenant_id=?
                ), status), updated_at=now()
                where id=? and tenant_id=?
                """, stageId, stageId, tenantId, recordId, tenantId);
    }

    private void updateField(UUID tenantId, String moduleKey, UUID recordId, Map<String, Object> action) {
        String fieldKey = string(action.get("fieldKey"));
        Object value = action.get("value");
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new BadRequestException("Workflow update_field requires fieldKey");
        }
        switch (fieldKey) {
            case "title" -> jdbc.update("update crm_records set title=?, updated_at=now() where id=? and tenant_id=?",
                    string(value), recordId, tenantId);
            case "status" -> jdbc.update("update crm_records set status=?, updated_at=now() where id=? and tenant_id=?",
                    string(value), recordId, tenantId);
            case "source" -> jdbc.update("update crm_records set source=?, updated_at=now() where id=? and tenant_id=?",
                    string(value), recordId, tenantId);
            case "priority" -> jdbc.update("update crm_records set priority=?, updated_at=now() where id=? and tenant_id=?",
                    string(value), recordId, tenantId);
            case "amount" -> jdbc.update("update crm_records set amount=?, updated_at=now() where id=? and tenant_id=?",
                    decimal(value), recordId, tenantId);
            default -> updateCustomField(tenantId, moduleKey, recordId, fieldKey, value);
        }
    }

    private void updateCustomField(UUID tenantId, String moduleKey, UUID recordId, String fieldKey, Object value) {
        List<UUID> fieldIds = jdbc.query("""
                select id from custom_fields
                where tenant_id=? and module_key=? and field_key=? and soft_deleted_at is null
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId, moduleKey, fieldKey);
        if (fieldIds.isEmpty()) {
            throw new BadRequestException("Unknown custom field: " + fieldKey);
        }
        jdbc.update("""
                insert into crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_text)
                values (?,?,?,?,?)
                on conflict (record_id, field_key) do update
                set value_text=excluded.value_text, value_number=null, value_boolean=null,
                    value_date=null, value_datetime=null, value_json=null, file_url=null, updated_at=now()
                """, tenantId, recordId, fieldIds.get(0), fieldKey, string(value));
    }

    private void createTask(UUID tenantId, UUID recordId, Map<String, Object> action) {
        String title = string(action.getOrDefault("title", "Workflow task"));
        UUID ownerUserId = uuid(action.get("ownerUserId"), action.get("userId"));
        jdbc.update("""
                insert into tasks
                (tenant_id, title, description, due_at, priority, status, related_object_type, related_object_id, owner_user_id)
                values (?,?,?,?,?,'OPEN','CRM_RECORD',?,?)
                """, tenantId, title, string(action.get("description")),
                instant(action.get("dueAt")), string(action.getOrDefault("priority", "NORMAL")),
                recordId, ownerUserId);
    }

    private void sendNotification(UUID tenantId, String moduleKey, UUID recordId, Map<String, Object> action) {
        UUID userId = uuid(action.get("userId"), action.get("ownerUserId"));
        String title = string(action.getOrDefault("title", "Workflow notification"));
        String message = string(action.getOrDefault("message", "A " + moduleKey + " workflow ran."));
        notifications.notify(tenantId, userId, "WORKFLOW", title, message,
                "CRM_RECORD", recordId, "workflow-" + recordId + "-" + title);
    }

    private void addTag(UUID tenantId, UUID recordId, String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        jdbc.update("""
                insert into record_tags (tenant_id, record_id, tag)
                values (?,?,?)
                on conflict (tenant_id, record_id, tag) do nothing
                """, tenantId, recordId, tag);
    }

    private UUID uuid(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return UUID.fromString(String.valueOf(value));
            }
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Instant instant(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value);
        return Instant.parse(text.endsWith("Z") || text.contains("+") ? text : text + "Z");
    }

    private record WorkflowRuleRuntime(UUID id, String actionsJson) {
    }
}
