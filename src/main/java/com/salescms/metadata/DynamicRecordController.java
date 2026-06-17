package com.salescms.metadata;

import com.salescms.platform.rbac.PermissionService;
import com.salescms.platform.tenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.salescms.metadata.MetadataDtos.AssignRecordRequest;
import static com.salescms.metadata.MetadataDtos.MoveRecordRequest;
import static com.salescms.metadata.MetadataDtos.RecordDto;
import static com.salescms.metadata.MetadataDtos.RecordRequest;

@RestController
@RequestMapping("/api")
public class DynamicRecordController {

    private final DynamicRecordService records;
    private final PermissionService permissions;

    public DynamicRecordController(DynamicRecordService records, PermissionService permissions) {
        this.records = records;
        this.permissions = permissions;
    }

    @GetMapping("/modules/{moduleKey}/records")
    @PreAuthorize("@permissionService.hasModuleAction(#moduleKey,'view')")
    public Page<RecordDto> list(@PathVariable String moduleKey,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "false") boolean mine) {
        UUID owner = mine || assignedOnly(moduleKey) ? TenantContext.requireUserId() : null;
        return records.list(moduleKey, q, owner,
                PageRequest.of(page, size, Sort.by("updatedAt").descending().and(Sort.by("title"))));
    }

    @PostMapping("/modules/{moduleKey}/records")
    @PreAuthorize("@permissionService.hasModuleAction(#moduleKey,'create')")
    public RecordDto create(@PathVariable String moduleKey, @RequestBody RecordRequest request) {
        return records.create(moduleKey, request);
    }

    @GetMapping("/records/{recordId}")
    public RecordDto get(@PathVariable UUID recordId) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "view");
        return records.get(recordId);
    }

    /** Records in {@code moduleKey} whose {@code fieldKey} LOOKUP points at this record. */
    @GetMapping("/records/{recordId}/related")
    public Page<RecordDto> related(@PathVariable UUID recordId,
                                   @RequestParam String moduleKey,
                                   @RequestParam String fieldKey,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "view");
        if (!permissions.hasModuleAction(moduleKey, "view")) {
            throw new AccessDeniedException("Missing view permission for " + moduleKey);
        }
        return records.related(recordId, moduleKey, fieldKey,
                PageRequest.of(page, size, Sort.by("updatedAt").descending().and(Sort.by("title"))));
    }

    @PutMapping("/records/{recordId}")
    public RecordDto update(@PathVariable UUID recordId, @RequestBody RecordRequest request) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "update");
        return records.update(recordId, request);
    }

    @DeleteMapping("/records/{recordId}")
    public void delete(@PathVariable UUID recordId) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "delete");
        records.delete(recordId);
    }

    @PostMapping("/records/{recordId}/move")
    public RecordDto move(@PathVariable UUID recordId, @RequestBody MoveRecordRequest request) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "update");
        return records.move(recordId, request.stageId(), request.status(), request.lostReason());
    }

    @PostMapping("/records/{recordId}/assign")
    public RecordDto assign(@PathVariable UUID recordId, @RequestBody AssignRecordRequest request) {
        CrmRecord record = records.find(recordId);
        requireRecordAccess(record, "assign");
        return records.assign(recordId, request.ownerUserId());
    }

    private void requireRecordAccess(CrmRecord record, String action) {
        if (!permissions.hasModuleAction(record.getModuleKey(), action)) {
            throw new AccessDeniedException("Missing " + action + " permission for " + record.getModuleKey());
        }
        if ("view".equals(action) && assignedOnly(record.getModuleKey())
                && !TenantContext.requireUserId().equals(record.getOwnerUserId())) {
            throw new AccessDeniedException("Record is outside the assigned scope.");
        }
    }

    private boolean assignedOnly(String moduleKey) {
        if (permissions.hasDynamicModuleAction(moduleKey, "view")
                || permissions.hasAnyPermission("LEAD_VIEW_ALL", "LEAD_VIEW_TEAM", "PO_VIEW_ALL")) {
            return false;
        }
        return permissions.hasAnyPermission("LEAD_VIEW_ASSIGNED", "PO_VIEW_OWN");
    }
}
