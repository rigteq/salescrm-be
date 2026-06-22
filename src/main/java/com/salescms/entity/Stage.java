package com.salescms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stages")
public class Stage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int probability;

    @Column(name = "is_won", nullable = false)
    private boolean won;

    @Column(name = "is_lost", nullable = false)
    private boolean lost;

    @Column(nullable = false)
    private String color = "#4f46e5";

    @Column(nullable = false)
    private int sequence;

    @Column(name = "stage_status", nullable = false)
    private String stageStatus = "OPEN";

    @Column(name = "outcome_type")
    private String outcomeType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Stage() {
    }

    public Stage(UUID tenantId, UUID pipelineId, String name, int position,
                 int probability, boolean won, boolean lost) {
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.name = name;
        this.position = position;
        this.sequence = position;
        this.probability = probability;
        this.won = won;
        this.lost = lost;
        this.stageStatus = won || lost ? "CLOSED" : "OPEN";
        this.outcomeType = won ? "WON" : lost ? "LOST" : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPosition() {
        return position;
    }

    public int getProbability() {
        return probability;
    }

    public void setProbability(int probability) {
        this.probability = probability;
    }

    public boolean isWon() {
        return won;
    }

    public boolean isLost() {
        return lost;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
        this.position = sequence;
    }

    public String getStageStatus() {
        return stageStatus;
    }

    public void setStageStatus(String stageStatus) {
        this.stageStatus = stageStatus;
    }

    public String getOutcomeType() {
        return outcomeType;
    }

    public void setOutcomeType(String outcomeType) {
        this.outcomeType = outcomeType;
        this.won = "WON".equals(outcomeType);
        this.lost = "LOST".equals(outcomeType);
    }
}
