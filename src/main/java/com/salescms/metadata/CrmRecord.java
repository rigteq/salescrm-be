package com.salescms.metadata;

import com.salescms.platform.common.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crm_records")
public class CrmRecord extends TenantOwnedEntity {

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(name = "stage_id")
    private UUID stageId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status = "OPEN";

    private String source;

    private String priority;

    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "finance_handoff_status")
    private String financeHandoffStatus;

    @Column(name = "finance_handoff_at")
    private Instant financeHandoffAt;

    @Column(name = "legacy_object_type")
    private String legacyObjectType;

    @Column(name = "legacy_object_id")
    private UUID legacyObjectId;

    protected CrmRecord() {
    }

    public CrmRecord(String moduleKey, String title) {
        this.moduleKey = moduleKey;
        this.title = title;
    }

    public String getModuleKey() { return moduleKey; }
    public UUID getPipelineId() { return pipelineId; }
    public void setPipelineId(UUID pipelineId) { this.pipelineId = pipelineId; }
    public UUID getStageId() { return stageId; }
    public void setStageId(UUID stageId) { this.stageId = stageId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { if (currency != null) this.currency = currency; }
    public String getLostReason() { return lostReason; }
    public void setLostReason(String lostReason) { this.lostReason = lostReason; }
    public String getFinanceHandoffStatus() { return financeHandoffStatus; }
    public Instant getFinanceHandoffAt() { return financeHandoffAt; }
    public boolean markFinanceHandoffPending() {
        if ("AWAITING_INVOICE".equals(financeHandoffStatus)) {
            return false;
        }
        financeHandoffStatus = "AWAITING_INVOICE";
        financeHandoffAt = Instant.now();
        return true;
    }
    public void clearFinanceHandoff() {
        financeHandoffStatus = null;
        financeHandoffAt = null;
    }
    public String getLegacyObjectType() { return legacyObjectType; }
    public UUID getLegacyObjectId() { return legacyObjectId; }
}
