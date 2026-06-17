package com.salescms.activity;

import com.salescms.platform.audit.AuditService;
import com.salescms.platform.common.BadRequestException;
import com.salescms.platform.common.NotFoundException;
import com.salescms.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    public record TaskRequest(
            @NotBlank String title,
            String description,
            Instant dueAt,
            String priority,
            String relatedObjectType,
            UUID relatedObjectId,
            UUID ownerUserId) {
    }

    public record TaskView(
            UUID id, String title, String description, Instant dueAt, String priority,
            String status, Instant completedAt, String relatedObjectType, UUID relatedObjectId,
            UUID ownerUserId, Instant createdAt, Instant updatedAt) {

        static TaskView of(TaskItem t) {
            return new TaskView(t.getId(), t.getTitle(), t.getDescription(), t.getDueAt(),
                    t.getPriority(), t.getStatus(), t.getCompletedAt(),
                    t.getRelatedObjectType(), t.getRelatedObjectId(),
                    t.getOwnerUserId(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    private final TaskRepository tasks;
    private final AuditService audit;

    public TaskController(TaskRepository tasks, AuditService audit) {
        this.tasks = tasks;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public Page<TaskView> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "false") boolean mine) {
        UUID tenantId = TenantContext.requireTenantId();
        // Postgres sorts NULLs last on ASC by default; explicit nullsLast() is
        // unsupported by Hibernate's criteria engine and 500s.
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("dueAt"), Sort.Order.desc("createdAt")));
        Page<TaskItem> result;
        if (mine) {
            result = tasks.findByTenantIdAndOwnerUserIdAndStatusAndSoftDeletedAtIsNull(
                    tenantId, TenantContext.requireUserId(),
                    status != null ? status : "OPEN", pageable);
        } else if (status != null && !status.isBlank()) {
            result = tasks.findByTenantIdAndStatusAndSoftDeletedAtIsNull(tenantId, status, pageable);
        } else {
            result = tasks.findByTenantIdAndSoftDeletedAtIsNull(tenantId, pageable);
        }
        return result.map(TaskView::of);
    }

    @GetMapping("/by-record")
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public List<TaskView> byRecord(@RequestParam String objectType, @RequestParam UUID objectId) {
        return tasks.findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNull(
                        TenantContext.requireTenantId(), objectType, objectId).stream()
                .map(TaskView::of)
                .toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('LEAD_UPDATE')")
    public TaskView create(@Valid @RequestBody TaskRequest request) {
        TaskItem task = new TaskItem(request.title());
        apply(task, request);
        task = tasks.save(task);
        audit.record("CREATE", "TASK", task.getId());
        return TaskView.of(task);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('LEAD_UPDATE')")
    public TaskView update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        TaskItem task = find(id);
        task.setTitle(request.title());
        apply(task, request);
        audit.record("UPDATE", "TASK", task.getId());
        return TaskView.of(tasks.save(task));
    }

    @PostMapping("/{id}/status")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('LEAD_UPDATE')")
    public TaskView setStatus(@PathVariable UUID id, @RequestParam String value) {
        TaskItem task = find(id);
        switch (value) {
            case "COMPLETED" -> task.complete();
            case "OPEN" -> task.reopen();
            case "CANCELLED" -> task.cancel();
            default -> throw new BadRequestException("Invalid task status: " + value);
        }
        audit.record("STATUS", "TASK", id);
        return TaskView.of(tasks.save(task));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('LEAD_DELETE')")
    public void delete(@PathVariable UUID id) {
        TaskItem task = find(id);
        task.softDelete();
        tasks.save(task);
        audit.record("DELETE", "TASK", id);
    }

    private TaskItem find(UUID id) {
        return tasks.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Task", id));
    }

    private void apply(TaskItem task, TaskRequest request) {
        task.setDescription(request.description());
        task.setDueAt(request.dueAt());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setRelatedObjectType(request.relatedObjectType());
        task.setRelatedObjectId(request.relatedObjectId());
        if (request.ownerUserId() != null) {
            task.setOwnerUserId(request.ownerUserId());
        }
    }
}
