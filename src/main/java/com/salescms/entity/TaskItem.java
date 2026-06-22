package com.salescms.entity;

import com.salescms.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskItem extends TenantOwnedEntity {

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "related_object_type")
    private String relatedObjectType;

    @Column(name = "related_object_id")
    private UUID relatedObjectId;

    protected TaskItem() {
    }

    public TaskItem(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getRelatedObjectType() {
        return relatedObjectType;
    }

    public void setRelatedObjectType(String relatedObjectType) {
        this.relatedObjectType = relatedObjectType;
    }

    public UUID getRelatedObjectId() {
        return relatedObjectId;
    }

    public void setRelatedObjectId(UUID relatedObjectId) {
        this.relatedObjectId = relatedObjectId;
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }

    public void reopen() {
        this.status = "OPEN";
        this.completedAt = null;
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}
