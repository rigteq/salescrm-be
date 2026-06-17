package com.salescms.platform.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<AuditLog> findByTenantIdAndObjectTypeAndObjectIdOrderByCreatedAtDesc(
            UUID tenantId, String objectType, UUID objectId);
}
