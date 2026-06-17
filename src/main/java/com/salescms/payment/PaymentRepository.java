package com.salescms.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByTenantIdAndRecordIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
            UUID tenantId, UUID recordId);

    Optional<Payment> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);
}
