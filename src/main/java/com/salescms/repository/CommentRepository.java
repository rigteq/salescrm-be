package com.salescms.repository;
import com.salescms.entity.Comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
            UUID tenantId, String relatedObjectType, UUID relatedObjectId);

    Optional<Comment> findByIdAndTenantIdAndSoftDeletedAtIsNull(UUID id, UUID tenantId);
}
