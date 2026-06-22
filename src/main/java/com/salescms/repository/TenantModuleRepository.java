package com.salescms.repository;
import com.salescms.entity.TenantModule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    List<TenantModule> findByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    List<TenantModule> findByTenantIdAndEnabledTrueOrderByDisplayOrderAsc(UUID tenantId);

    Optional<TenantModule> findByTenantIdAndModuleKey(UUID tenantId, String moduleKey);

    boolean existsByTenantIdAndModuleKey(UUID tenantId, String moduleKey);

    long countByTenantId(UUID tenantId);
}
