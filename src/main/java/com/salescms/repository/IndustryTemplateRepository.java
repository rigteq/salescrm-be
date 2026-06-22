package com.salescms.repository;
import com.salescms.entity.IndustryTemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndustryTemplateRepository extends JpaRepository<IndustryTemplate, UUID> {

    Optional<IndustryTemplate> findByTemplateKey(String templateKey);

    Optional<IndustryTemplate> findByIdAndActiveTrue(UUID id);

    List<IndustryTemplate> findByActiveTrueOrderByNameAsc();
}
