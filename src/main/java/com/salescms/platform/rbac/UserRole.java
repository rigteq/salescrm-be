package com.salescms.platform.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRole {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "primary_role", nullable = false)
    private boolean primaryRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserRole() {
    }

    public UserRole(UUID userId, UUID roleId, UUID companyId, boolean primaryRole) {
        this.userId = userId;
        this.roleId = roleId;
        this.companyId = companyId;
        this.primaryRole = primaryRole;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public boolean isPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(boolean primaryRole) {
        this.primaryRole = primaryRole;
    }
}
