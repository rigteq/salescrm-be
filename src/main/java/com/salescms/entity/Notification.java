package com.salescms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    private String message;

    @Column(name = "link_object_type")
    private String linkObjectType;

    @Column(name = "link_object_id")
    private UUID linkObjectId;

    @Column(name = "dedupe_key")
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(UUID tenantId, UUID userId, String type, String title, String message,
                        String linkObjectType, UUID linkObjectId, String dedupeKey) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.linkObjectType = linkObjectType;
        this.linkObjectId = linkObjectId;
        this.dedupeKey = dedupeKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLinkObjectType() {
        return linkObjectType;
    }

    public UUID getLinkObjectId() {
        return linkObjectId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
