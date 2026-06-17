package com.salescms.metadata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crm_record_custom_values")
public class CrmRecordCustomValue {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "field_id")
    private UUID fieldId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_datetime")
    private Instant valueDatetime;

    @Column(name = "value_json")
    private String valueJson;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "value_record_id")
    private UUID valueRecordId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CrmRecordCustomValue() {
    }

    public CrmRecordCustomValue(UUID tenantId, UUID recordId, UUID fieldId, String fieldKey) {
        this.tenantId = tenantId;
        this.recordId = recordId;
        this.fieldId = fieldId;
        this.fieldKey = fieldKey;
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
    public UUID getRecordId() { return recordId; }
    public UUID getFieldId() { return fieldId; }
    public String getFieldKey() { return fieldKey; }
    public String getValueText() { return valueText; }
    public BigDecimal getValueNumber() { return valueNumber; }
    public Boolean getValueBoolean() { return valueBoolean; }
    public LocalDate getValueDate() { return valueDate; }
    public Instant getValueDatetime() { return valueDatetime; }
    public String getValueJson() { return valueJson; }
    public String getFileUrl() { return fileUrl; }
    public UUID getValueRecordId() { return valueRecordId; }

    public void clearValues() {
        valueText = null;
        valueNumber = null;
        valueBoolean = null;
        valueDate = null;
        valueDatetime = null;
        valueJson = null;
        fileUrl = null;
        valueRecordId = null;
    }

    public void setValueText(String valueText) { this.valueText = valueText; }
    public void setValueNumber(BigDecimal valueNumber) { this.valueNumber = valueNumber; }
    public void setValueBoolean(Boolean valueBoolean) { this.valueBoolean = valueBoolean; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public void setValueDatetime(Instant valueDatetime) { this.valueDatetime = valueDatetime; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public void setValueRecordId(UUID valueRecordId) { this.valueRecordId = valueRecordId; }
}
