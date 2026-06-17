package com.salescms.metadata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CrmRecordRepository extends JpaRepository<CrmRecord, UUID> {

    @Query("""
            select r from CrmRecord r
            where r.tenantId = :tenantId
              and r.moduleKey = :moduleKey
              and r.softDeletedAt is null
              and (:ownerUserId is null or r.ownerUserId = :ownerUserId)
              and (:q is null or lower(r.title) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<CrmRecord> search(@Param("tenantId") UUID tenantId,
                           @Param("moduleKey") String moduleKey,
                           @Param("ownerUserId") UUID ownerUserId,
                           @Param("q") String q,
                           Pageable pageable);

    Optional<CrmRecord> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
            select r from CrmRecord r
            where r.tenantId = :tenantId
              and r.moduleKey = :moduleKey
              and r.softDeletedAt is null
              and exists (
                  select 1 from CrmRecordCustomValue v
                  where v.recordId = r.id
                    and v.tenantId = :tenantId
                    and v.fieldKey = :fieldKey
                    and v.valueRecordId = :targetRecordId)
            """)
    Page<CrmRecord> findByLookup(@Param("tenantId") UUID tenantId,
                                 @Param("moduleKey") String moduleKey,
                                 @Param("fieldKey") String fieldKey,
                                 @Param("targetRecordId") UUID targetRecordId,
                                 Pageable pageable);
}
