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
@Table(name = "role_permissions")
public class RolePermission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RolePermission() {
    }

    public RolePermission(UUID roleId, UUID permissionId, UUID companyId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.companyId = companyId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public UUID getCompanyId() {
        return companyId;
    }
}
