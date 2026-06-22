package com.salescms.service;
import com.salescms.repository.AuditLogRepository;
import com.salescms.entity.AuditLog;

import com.salescms.entity.TenantContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String objectType, UUID objectId) {
        record(action, objectType, objectId, null);
    }

    public void record(String action, String objectType, UUID objectId, Map<String, Object> detail) {
        repository.save(new AuditLog(
                TenantContext.requireTenantId(),
                TenantContext.requireUserId(),
                action, objectType, objectId, detail));
    }
}
