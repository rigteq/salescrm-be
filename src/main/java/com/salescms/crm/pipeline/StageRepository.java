package com.salescms.crm.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StageRepository extends JpaRepository<Stage, UUID> {

    List<Stage> findByTenantIdAndPipelineIdOrderByPositionAsc(UUID tenantId, UUID pipelineId);

    List<Stage> findByTenantIdOrderByPositionAsc(UUID tenantId);

    Optional<Stage> findByIdAndTenantId(UUID id, UUID tenantId);
}
