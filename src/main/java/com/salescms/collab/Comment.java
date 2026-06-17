package com.salescms.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "related_object_type", nullable = false)
    private String relatedObjectType;

    @Column(name = "related_object_id", nullable = false)
    private UUID relatedObjectId;

    @Column(nullable = false)
    private String body;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "soft_deleted_at")
    private Instant softDeletedAt;

    protected Comment() {
    }

    public Comment(UUID tenantId, String relatedObjectType, UUID relatedObjectId,
                   String body, UUID authorUserId) {
        this.tenantId = tenantId;
        this.relatedObjectType = relatedObjectType;
        this.relatedObjectId = relatedObjectId;
        this.body = body;
        this.authorUserId = authorUserId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getRelatedObjectType() {
        return relatedObjectType;
    }

    public UUID getRelatedObjectId() {
        return relatedObjectId;
    }

    public String getBody() {
        return body;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void softDelete() {
        this.softDeletedAt = Instant.now();
    }
}
