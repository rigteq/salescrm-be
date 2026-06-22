package com.salescms.repository;
import com.salescms.entity.UserRole;
import com.salescms.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    @Query("""
            select ur.userId, r.id, ur.companyId, r.code, r.name, r.hierarchyLevel, ur.primaryRole
            from UserRole ur
            join Role r on r.id = ur.roleId
            where ur.userId = :userId
              and ur.companyId = :companyId
              and r.deleted = false
            order by r.hierarchyLevel asc, r.name asc
            """)
    List<Object[]> findAccessRoleRows(@Param("userId") UUID userId, @Param("companyId") UUID companyId);

    Optional<UserRole> findByUserIdAndCompanyIdAndPrimaryRoleTrue(UUID userId, UUID companyId);

    Optional<UserRole> findByUserIdAndRoleIdAndCompanyId(UUID userId, UUID roleId, UUID companyId);

    long countByRoleId(UUID roleId);

    void deleteByUserIdAndRoleIdAndCompanyId(UUID userId, UUID roleId, UUID companyId);
}
