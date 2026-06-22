package com.salescms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false)
    private String action;

    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_id")
    private UUID objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(UUID tenantId, UUID actorUserId, String action,
                    String objectType, UUID objectId, Map<String, Object> detail) {
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
