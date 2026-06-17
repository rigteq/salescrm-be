package com.salescms.activity;

import com.salescms.platform.audit.AuditService;
import com.salescms.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    public record ActivityRequest(
            @NotBlank String type,
            @NotBlank String subject,
            String body,
            Instant occurredAt,
            @NotBlank String relatedObjectType,
            @NotNull UUID relatedObjectId) {
    }

    public record ActivityView(
            UUID id, String type, String subject, String body, Instant occurredAt,
            String relatedObjectType, UUID relatedObjectId, UUID ownerUserId, Instant createdAt) {

        static ActivityView of(Activity a) {
            return new ActivityView(a.getId(), a.getType(), a.getSubject(), a.getBody(),
                    a.getOccurredAt(), a.getRelatedObjectType(), a.getRelatedObjectId(),
                    a.getOwnerUserId(), a.getCreatedAt());
        }
    }

    private final ActivityRepository activities;
    private final AuditService audit;

    public ActivityController(ActivityRepository activities, AuditService audit) {
        this.activities = activities;
        this.audit = audit;
    }

    /** Timeline of one record. */
    @GetMapping("/by-record")
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public List<ActivityView> byRecord(@RequestParam String objectType, @RequestParam UUID objectId) {
        return activities
                .findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNullOrderByOccurredAtDesc(
                        TenantContext.requireTenantId(), objectType, objectId).stream()
                .map(ActivityView::of)
                .toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('LEAD_UPDATE')")
    public ActivityView create(@Valid @RequestBody ActivityRequest request) {
        Activity activity = new Activity(request.type(), request.subject(),
                request.relatedObjectType(), request.relatedObjectId());
        activity.setBody(request.body());
        if (request.occurredAt() != null) {
            activity.setOccurredAt(request.occurredAt());
        }
        activity = activities.save(activity);
        audit.record("CREATE", "ACTIVITY", activity.getId());
        return ActivityView.of(activity);
    }
}
