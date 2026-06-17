package com.salescms.platform.notification;

import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.tenancy.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record NotificationView(UUID id, String type, String title, String message,
                                   String linkObjectType, UUID linkObjectId,
                                   boolean read, Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getLinkObjectType(), n.getLinkObjectId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record Inbox(long unread, List<NotificationView> items) {
    }

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.hasPermission('NOTIFICATION_VIEW')")
    public Inbox list() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        List<NotificationView> items = repository.findInbox(tenantId, userId, PageRequest.of(0, 30))
                .stream().map(NotificationView::of).toList();
        return new Inbox(repository.countUnread(tenantId, userId), items);
    }

    @PostMapping("/{id}/read")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('NOTIFICATION_VIEW')")
    public void markRead(@PathVariable UUID id) {
        Notification n = repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Notification", id));
        n.markRead();
        repository.save(n);
    }

    @PostMapping("/read-all")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('NOTIFICATION_VIEW')")
    public void markAllRead() {
        repository.markAllRead(TenantContext.requireTenantId(), TenantContext.requireUserId(), Instant.now());
    }
}
