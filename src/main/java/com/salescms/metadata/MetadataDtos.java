package com.salescms.metadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MetadataDtos {

    private MetadataDtos() {
    }

    public record ModuleDto(UUID id, String moduleKey, boolean enabled, String singularLabel,
                            String pluralLabel, String icon, int displayOrder) {
        static ModuleDto of(TenantModule module) {
            return new ModuleDto(module.getId(), module.getModuleKey(), module.isEnabled(),
                    module.getSingularLabel(), module.getPluralLabel(), module.getIcon(),
                    module.getDisplayOrder());
        }
    }

    public record ModuleUpdateRequest(List<ModuleDto> modules) {
    }

    public record CustomFieldDto(UUID id, String moduleKey, String fieldKey, String label,
                                 String fieldType, String optionsJson, String validationJson,
                                 String visibilityRulesJson, boolean required, int displayOrder,
                                 boolean systemField) {
        static CustomFieldDto of(CustomField field) {
            return new CustomFieldDto(field.getId(), field.getModuleKey(), field.getFieldKey(),
                    field.getLabel(), field.getFieldType(), field.getOptionsJson(),
                    field.getValidationJson(), field.getVisibilityRulesJson(), field.isRequired(),
                    field.getDisplayOrder(), field.isSystemField());
        }
    }

    public record CustomFieldRequest(String moduleKey, String fieldKey, String label, String fieldType,
                                     String optionsJson, String validationJson, String visibilityRulesJson,
                                     boolean required, int displayOrder) {
    }

    public record RecordRequest(String title, String status, String source, String priority,
                                BigDecimal amount, String currency, UUID pipelineId, UUID stageId,
                                UUID ownerUserId, Map<String, Object> customValues) {
    }

    public record RecordDto(UUID id, String moduleKey, UUID pipelineId, UUID stageId, UUID ownerUserId,
                            String title, String status, String source, String priority,
                            BigDecimal amount, String currency, Instant createdAt, Instant updatedAt,
                            String lostReason, String financeHandoffStatus, Instant financeHandoffAt,
                            Map<String, Object> customValues) {
        static RecordDto of(CrmRecord record, Map<String, Object> values) {
            return new RecordDto(record.getId(), record.getModuleKey(), record.getPipelineId(),
                    record.getStageId(), record.getOwnerUserId(), record.getTitle(), record.getStatus(),
                    record.getSource(), record.getPriority(), record.getAmount(), record.getCurrency(),
                    record.getCreatedAt(), record.getUpdatedAt(), record.getLostReason(),
                    record.getFinanceHandoffStatus(), record.getFinanceHandoffAt(), values);
        }
    }

    public record MoveRecordRequest(UUID stageId, String status, String lostReason) {
    }

    public record AssignRecordRequest(UUID ownerUserId) {
    }

    public record TemplateDto(UUID id, String templateKey, String name, String description,
                              String businessType, boolean active) {
        static TemplateDto of(IndustryTemplate template) {
            return new TemplateDto(template.getId(), template.getTemplateKey(), template.getName(),
                    template.getDescription(), template.getBusinessType(), template.isActive());
        }
    }

    public record TemplateRequest(String templateKey, String name, String description, String businessType,
                                  boolean active) {
    }

    public record TemplateDetailDto(TemplateDto template, List<ModuleDto> modules,
                                    List<PipelineDto> pipelines, List<CustomFieldDto> fields) {
    }

    public record ClonePreviewDto(UUID templateId, String templateKey, String name,
                                  int moduleCount, int pipelineCount, int stageCount,
                                  int fieldCount, int formCount, int workflowCount,
                                  int dashboardCount) {
    }

    public record SetupStateDto(boolean completed, String selectedTemplateKey,
                                List<TemplateDto> templates, List<ModuleDto> modules) {
    }

    public record SelectTemplateRequest(UUID templateId) {
    }

    public record StageDto(UUID id, String name, int position, int probability,
                           boolean won, boolean lost, String color, int sequence,
                           String stageStatus, String outcomeType) {
    }

    public record PipelineDto(UUID id, String moduleKey, String name, boolean isDefault,
                              List<StageDto> stages) {
    }

    public record StageRequest(UUID id, String name, int probability, String color,
                               int sequence, String stageStatus, String outcomeType) {
    }

    public record PipelineRequest(String moduleKey, String name, boolean isDefault,
                                  List<StageRequest> stages) {
    }

    public record FormDto(UUID id, String moduleKey, String name, String description,
                          UUID pipelineId, UUID defaultStageId, boolean active) {
    }

    public record FormRequest(String moduleKey, String name, String description,
                              UUID pipelineId, UUID defaultStageId, boolean active,
                              List<UUID> fieldIds) {
    }

    public record WorkflowDto(UUID id, String moduleKey, String name, String triggerType,
                              String conditionsJson, String actionsJson, boolean active) {
    }

    public record WorkflowRequest(String moduleKey, String name, String triggerType,
                                  String conditionsJson, String actionsJson, boolean active) {
    }

    public record TeamDto(UUID id, String name, String description, boolean active) {
    }

    public record TeamRequest(String name, String description, boolean active) {
    }

    public record TeamMemberDto(UUID id, UUID teamId, UUID userId, String roleLabel) {
    }

    public record TeamMemberRequest(UUID userId, String roleLabel) {
    }

    public record AssignmentRuleDto(UUID id, String moduleKey, String name, String assignmentType,
                                    String conditionsJson, int priorityOrder, boolean active) {
    }

    public record AssignmentRuleRequest(String moduleKey, String name, String assignmentType,
                                        String conditionsJson, int priorityOrder, boolean active) {
    }

    public record DashboardWidgetDto(UUID id, UUID dashboardId, String moduleKey, String title,
                                     String widgetType, String metricKey, String filtersJson,
                                     String chartType, int displayOrder) {
    }

    public record DashboardWidgetRequest(UUID dashboardId, String moduleKey, String title,
                                         String widgetType, String metricKey, String filtersJson,
                                         String chartType, int displayOrder) {
    }

    public record GenericConfigDto(UUID id, String name, String moduleKey, String type,
                                   String conditionsJson, String actionsJson, Integer priorityOrder,
                                   boolean active) {
    }
}
