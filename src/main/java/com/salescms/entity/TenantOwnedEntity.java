package com.salescms.entity;

import com.salescms.entity.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform metadata carried by every tenant-owned business record, per the
 * design report: tenant_id, owner_user_id, created_by, updated_by, version,
 * soft_deleted_at and timestamps. Tenant and actor are stamped automatically
 * from {@link TenantContext}.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "soft_deleted_at")
    private Instant softDeletedAt;

    @PrePersist
    void onCreate() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenantId();
        }
        UUID actor = TenantContext.requireUserId();
        createdBy = actor;
        updatedBy = actor;
        if (ownerUserId == null) {
            ownerUserId = actor;
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedBy = TenantContext.requireUserId();
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getSoftDeletedAt() {
        return softDeletedAt;
    }

    public void softDelete() {
        this.softDeletedAt = Instant.now();
    }
}
