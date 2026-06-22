package com.salescms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "custom_fields")
public class CustomField {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @Column(name = "options_json", nullable = false)
    private String optionsJson = "{}";

    @Column(name = "validation_json", nullable = false)
    private String validationJson = "{}";

    @Column(name = "visibility_rules_json", nullable = false)
    private String visibilityRulesJson = "{}";

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "system_field", nullable = false)
    private boolean systemField;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "soft_deleted_at")
    private Instant softDeletedAt;

    protected CustomField() {
    }

    public CustomField(UUID tenantId, String moduleKey, String fieldKey, String label, String fieldType) {
        this.tenantId = tenantId;
        this.moduleKey = moduleKey;
        this.fieldKey = fieldKey;
        this.label = label;
        this.fieldType = fieldType;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getModuleKey() { return moduleKey; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson != null ? optionsJson : "{}"; }
    public String getValidationJson() { return validationJson; }
    public void setValidationJson(String validationJson) { this.validationJson = validationJson != null ? validationJson : "{}"; }
    public String getVisibilityRulesJson() { return visibilityRulesJson; }
    public void setVisibilityRulesJson(String visibilityRulesJson) { this.visibilityRulesJson = visibilityRulesJson != null ? visibilityRulesJson : "{}"; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isSystemField() { return systemField; }
    public void setSystemField(boolean systemField) { this.systemField = systemField; }
    public Instant getSoftDeletedAt() { return softDeletedAt; }
    public void softDelete() { this.softDeletedAt = Instant.now(); }
}
