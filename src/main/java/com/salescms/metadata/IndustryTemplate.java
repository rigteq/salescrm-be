package com.salescms.metadata;

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
@Table(name = "industry_templates")
public class IndustryTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IndustryTemplate() {
    }

    public IndustryTemplate(String templateKey, String name, String description, String businessType) {
        this.templateKey = templateKey;
        this.name = name;
        this.description = description;
        this.businessType = businessType;
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
    public String getTemplateKey() { return templateKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
