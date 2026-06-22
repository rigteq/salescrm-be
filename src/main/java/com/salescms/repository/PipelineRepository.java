package com.salescms.repository;
import com.salescms.entity.Pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    List<Pipeline> findByTenantIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(UUID tenantId);

    List<Pipeline> findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByCreatedAtAsc(UUID tenantId, String moduleKey);

    Optional<Pipeline> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Pipeline> findFirstByTenantIdAndIsDefaultTrue(UUID tenantId);

    Optional<Pipeline> findFirstByTenantIdAndModuleKeyAndIsDefaultTrue(UUID tenantId, String moduleKey);
}
