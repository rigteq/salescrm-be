package com.salescms.repository;
import com.salescms.entity.PriceBookEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceBookEntryRepository extends JpaRepository<PriceBookEntry, UUID> {

    List<PriceBookEntry> findByTenantIdAndPriceBookId(UUID tenantId, UUID priceBookId);

    Optional<PriceBookEntry> findByTenantIdAndPriceBookIdAndProductId(
            UUID tenantId, UUID priceBookId, UUID productId);
}
