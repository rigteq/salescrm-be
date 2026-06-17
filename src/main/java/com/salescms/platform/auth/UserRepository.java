package com.salescms.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByTenantIdOrderByFirstNameAsc(UUID tenantId);

    List<User> findByTenantIdAndDeletedFalseOrderByFirstNameAsc(UUID tenantId);

    long countByTenantIdAndDeletedFalse(UUID tenantId);

    long countByDeletedFalse();
}
