package com.salescms.repository;
import com.salescms.entity.Activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByTenantIdAndSoftDeletedAtIsNullOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

    List<Activity> findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNullOrderByOccurredAtDesc(
            UUID tenantId, String relatedObjectType, UUID relatedObjectId);
}
