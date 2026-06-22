package com.salescms.repository;
import com.salescms.entity.Permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCodeAndDeletedFalse(String code);

    Optional<Permission> findByIdAndDeletedFalse(UUID id);

    List<Permission> findByDeletedFalseOrderByModuleAscCodeAsc();
}
