package com.salescms.platform.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCodeAndCompanyIdIsNullAndDeletedFalse(String code);

    Optional<Role> findByCodeAndCompanyIdAndDeletedFalse(String code, UUID companyId);

    Optional<Role> findByIdAndDeletedFalse(UUID id);

    List<Role> findByDeletedFalseOrderByHierarchyLevelAscNameAsc();

    List<Role> findByCompanyIdAndDeletedFalseOrderByHierarchyLevelAscNameAsc(UUID companyId);

    List<Role> findByCompanyIdIsNullAndDeletedFalseOrderByHierarchyLevelAscNameAsc();

    @Query("""
            select r from Role r
            where r.deleted = false
              and (r.companyId is null or r.companyId = :companyId)
            order by r.hierarchyLevel asc, r.name asc
            """)
    List<Role> findAssignableCatalogForCompany(@Param("companyId") UUID companyId);
}
