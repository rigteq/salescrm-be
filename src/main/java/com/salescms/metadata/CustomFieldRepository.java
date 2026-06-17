package com.salescms.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {

    List<CustomField> findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(
            UUID tenantId, String moduleKey);

    List<CustomField> findByTenantIdAndSoftDeletedAtIsNullOrderByModuleKeyAscDisplayOrderAsc(UUID tenantId);

    Optional<CustomField> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<CustomField> findByTenantIdAndModuleKeyAndFieldKeyAndSoftDeletedAtIsNull(
            UUID tenantId, String moduleKey, String fieldKey);
}
