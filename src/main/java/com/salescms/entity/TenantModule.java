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
@Table(name = "tenant_modules")
public class TenantModule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "singular_label", nullable = false)
    private String singularLabel;

    @Column(name = "plural_label", nullable = false)
    private String pluralLabel;

    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantModule() {
    }

    public TenantModule(UUID tenantId, String moduleKey, String singularLabel,
                        String pluralLabel, String icon, int displayOrder) {
        this.tenantId = tenantId;
        this.moduleKey = moduleKey;
        this.singularLabel = singularLabel;
        this.pluralLabel = pluralLabel;
        this.icon = icon;
        this.displayOrder = displayOrder;
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
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSingularLabel() { return singularLabel; }
    public void setSingularLabel(String singularLabel) { this.singularLabel = singularLabel; }
    public String getPluralLabel() { return pluralLabel; }
    public void setPluralLabel(String pluralLabel) { this.pluralLabel = pluralLabel; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
