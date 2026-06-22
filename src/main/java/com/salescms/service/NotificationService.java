package com.salescms.service;
import com.salescms.repository.NotificationRepository;
import com.salescms.entity.Notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a notification unless one with the same dedupeKey already exists
     * for the tenant (so the reminder job is safe to run repeatedly).
     */
    @Transactional
    public void notify(UUID tenantId, UUID userId, String type, String title, String message,
                       String linkObjectType, UUID linkObjectId, String dedupeKey) {
        if (dedupeKey != null && repository.existsByTenantIdAndDedupeKey(tenantId, dedupeKey)) {
            return;
        }
        repository.save(new Notification(tenantId, userId, type, title, message,
                linkObjectType, linkObjectId, dedupeKey));
    }
}
