package com.salescms.repository;
import com.salescms.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByTenantIdAndSoftDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Page<Product> findByTenantIdAndSoftDeletedAtIsNullAndNameContainingIgnoreCase(
            UUID tenantId, String name, Pageable pageable);

    Optional<Product> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);
}
