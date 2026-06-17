package com.salescms.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "selected_template_key")
    private String selectedTemplateKey;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted = false;

    @Column(name = "setup_completed_at")
    private Instant setupCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSelectedTemplateKey() {
        return selectedTemplateKey;
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }

    public void markTemplateSelected(String templateKey) {
        this.selectedTemplateKey = templateKey;
        this.updatedAt = Instant.now();
    }

    public void completeSetup() {
        this.setupCompleted = true;
        this.setupCompletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
