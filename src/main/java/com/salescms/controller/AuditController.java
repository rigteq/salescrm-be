package com.salescms.controller;
import com.salescms.repository.AuditLogRepository;
import com.salescms.entity.AuditLog;

import com.salescms.entity.TenantContext;
import com.salescms.service.PermissionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/audit", "/api/audit-logs"})
public class AuditController {

    public record AuditView(UUID id, UUID actorUserId, String action, String objectType,
                            UUID objectId, Map<String, Object> detail, Instant createdAt) {

        static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getActorUserId(), a.getAction(), a.getObjectType(),
                    a.getObjectId(), a.getDetail(), a.getCreatedAt());
        }
    }

    private final AuditLogRepository repository;
    private final PermissionService permissionService;

    public AuditController(AuditLogRepository repository, PermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('AUDIT_LOG_VIEW','PLATFORM_AUDIT_VIEW')")
    public List<AuditView> recent(@RequestParam(defaultValue = "50") int limit,
                                  @RequestParam(required = false) String objectType,
                                  @RequestParam(required = false) UUID objectId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (objectType != null && objectId != null) {
            return repository.findByTenantIdAndObjectTypeAndObjectIdOrderByCreatedAtDesc(
                            tenantId, objectType, objectId).stream()
                    .map(AuditView::of)
                    .toList();
        }
        if (permissionService.isPlatformOwner()) {
            return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 200)))
                    .stream()
                    .map(AuditView::of)
                    .toList();
        }
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(AuditView::of)
                .toList();
    }
}
