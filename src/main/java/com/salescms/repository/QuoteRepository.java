package com.salescms.repository;
import com.salescms.entity.Quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    Page<Quote> findByTenantIdAndSoftDeletedAtIsNull(UUID tenantId, Pageable pageable);

    List<Quote> findByTenantIdAndRecordIdAndSoftDeletedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId, UUID recordId);

    Optional<Quote> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);
}
