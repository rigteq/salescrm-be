package com.salescms.repository;
import com.salescms.entity.Notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Recipient sees notifications addressed to them plus tenant-wide ones (user_id null).
    @Query("""
            select n from Notification n
            where n.tenantId = :tenantId and (n.userId = :userId or n.userId is null)
            order by n.createdAt desc
            """)
    List<Notification> findInbox(@Param("tenantId") UUID tenantId,
                                 @Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.tenantId = :tenantId and (n.userId = :userId or n.userId is null)
              and n.readAt is null
            """)
    long countUnread(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.tenantId = :tenantId and (n.userId = :userId or n.userId is null)
              and n.readAt is null
            """)
    void markAllRead(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                     @Param("now") Instant now);
}
