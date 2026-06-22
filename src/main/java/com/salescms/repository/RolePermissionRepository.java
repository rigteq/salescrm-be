package com.salescms.repository;
import com.salescms.entity.RolePermission;
import com.salescms.entity.Permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    List<RolePermission> findByRoleIdIn(Collection<UUID> roleIds);

    @Query("""
            select distinct p
            from Permission p
            join RolePermission rp on rp.permissionId = p.id
            where rp.roleId in :roleIds
              and p.deleted = false
            order by p.module asc, p.code asc
            """)
    List<Permission> findPermissionsForRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    void deleteByRoleId(UUID roleId);
}
