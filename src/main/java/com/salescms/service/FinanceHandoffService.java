package com.salescms.service;

import com.salescms.entity.CrmRecord;
import com.salescms.service.AuditService;
import com.salescms.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceHandoffService {

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final AuditService audit;

    public FinanceHandoffService(JdbcTemplate jdbc, NotificationService notifications, AuditService audit) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public void handoffWonDeal(CrmRecord record) {
        List<UUID> recipients = financeRecipients(record.getTenantId());
        for (UUID userId : recipients) {
            notifications.notify(
                    record.getTenantId(),
                    userId,
                    "FINANCE_HANDOFF",
                    "Won deal ready for finance",
                    message(record),
                    "CRM_RECORD",
                    record.getId(),
                    "finance-handoff-" + record.getId() + "-" + userId);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleKey", record.getModuleKey());
        detail.put("status", record.getFinanceHandoffStatus());
        detail.put("recipientCount", recipients.size());
        audit.record("FINANCE_HANDOFF", "CRM_RECORD", record.getId(), detail);
    }

    private List<UUID> financeRecipients(UUID tenantId) {
        return jdbc.query("""
                select distinct u.id
                from users u
                left join user_roles ur on ur.user_id = u.id and ur.company_id = u.tenant_id
                left join roles r on r.id = ur.role_id and r.is_deleted = false
                where u.tenant_id = ?
                  and u.is_deleted = false
                  and u.status = 'ACTIVE'
                  and (u.role = 'FINANCE' or r.code = 'FINANCE')
                order by u.id
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId);
    }

    private String message(CrmRecord record) {
        String value = money(record.getAmount(), record.getCurrency());
        return value == null
                ? record.getTitle() + " was marked Won and is awaiting invoice setup."
                : record.getTitle() + " was marked Won for " + value + " and is awaiting invoice setup.";
    }

    private String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        return (currency == null || currency.isBlank() ? "USD" : currency) + " " + amount;
    }
}
