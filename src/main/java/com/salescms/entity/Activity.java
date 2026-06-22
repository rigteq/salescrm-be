package com.salescms.entity;

import com.salescms.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity extends TenantOwnedEntity {

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String subject;

    private String body;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "related_object_type", nullable = false)
    private String relatedObjectType;

    @Column(name = "related_object_id", nullable = false)
    private UUID relatedObjectId;

    protected Activity() {
    }

    public Activity(String type, String subject, String relatedObjectType, UUID relatedObjectId) {
        this.type = type;
        this.subject = subject;
        this.relatedObjectType = relatedObjectType;
        this.relatedObjectId = relatedObjectId;
    }

    public String getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getRelatedObjectType() {
        return relatedObjectType;
    }

    public UUID getRelatedObjectId() {
        return relatedObjectId;
    }
}
