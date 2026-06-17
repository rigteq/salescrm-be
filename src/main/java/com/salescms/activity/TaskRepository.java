package com.salescms.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskItem, UUID> {

    // System-wide (cross-tenant) scan for the reminder job.
    @Query("""
            select t from TaskItem t
            where t.status = 'OPEN' and t.softDeletedAt is null
              and t.dueAt is not null and t.dueAt <= :threshold
              and t.ownerUserId is not null
            """)
    List<TaskItem> findDueOpenTasks(@Param("threshold") Instant threshold);

    Page<TaskItem> findByTenantIdAndSoftDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Page<TaskItem> findByTenantIdAndStatusAndSoftDeletedAtIsNull(UUID tenantId, String status, Pageable pageable);

    Page<TaskItem> findByTenantIdAndOwnerUserIdAndStatusAndSoftDeletedAtIsNull(
            UUID tenantId, UUID ownerUserId, String status, Pageable pageable);

    List<TaskItem> findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNull(
            UUID tenantId, String relatedObjectType, UUID relatedObjectId);

    Optional<TaskItem> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);
}
