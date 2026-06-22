package com.salescms.repository;
import com.salescms.entity.CrmRecordCustomValue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmRecordCustomValueRepository extends JpaRepository<CrmRecordCustomValue, UUID> {

    List<CrmRecordCustomValue> findByTenantIdAndRecordId(UUID tenantId, UUID recordId);

    List<CrmRecordCustomValue> findByTenantIdAndRecordIdIn(UUID tenantId, Collection<UUID> recordIds);

    Optional<CrmRecordCustomValue> findByTenantIdAndRecordIdAndFieldKey(UUID tenantId, UUID recordId, String fieldKey);
}
