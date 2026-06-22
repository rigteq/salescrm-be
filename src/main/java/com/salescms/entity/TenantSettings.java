package com.salescms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "whatsapp_template")
    private String whatsappTemplate;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "USD";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TenantSettings() {
    }

    public TenantSettings(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getWhatsappTemplate() {
        return whatsappTemplate;
    }

    public void setWhatsappTemplate(String whatsappTemplate) {
        this.whatsappTemplate = whatsappTemplate;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
