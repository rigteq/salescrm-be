package com.salescms.controller;
import com.salescms.repository.PaymentRepository;
import com.salescms.entity.Payment;

import com.salescms.entity.CrmRecord;
import com.salescms.repository.CrmRecordRepository;
import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Set<String> KINDS = Set.of("RECEIVED", "DUE");

    public record PaymentRequest(
            @NotNull UUID recordId,
            String kind,
            @NotNull BigDecimal amount,
            String currency,
            LocalDate dueDate,
            LocalDate receivedDate,
            String note) {
    }

    public record PaymentView(UUID id, UUID recordId, String kind, BigDecimal amount,
                              String currency, LocalDate dueDate, LocalDate receivedDate, String note) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getRecordId(), p.getKind(), p.getAmount(),
                    p.getCurrency(), p.getDueDate(), p.getReceivedDate(), p.getNote());
        }
    }

    public record PaymentSummary(BigDecimal dealAmount, BigDecimal received, BigDecimal due,
                                 BigDecimal outstanding, List<PaymentView> payments) {
    }

    private final PaymentRepository payments;
    private final CrmRecordRepository records;
    private final AuditService audit;

    public PaymentController(PaymentRepository payments, CrmRecordRepository records,
                             AuditService audit) {
        this.payments = payments;
        this.records = records;
        this.audit = audit;
    }

    @GetMapping("/by-record/{recordId}")
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.hasAnyPermission('BILLING_VIEW','BILLING_MANAGE','PO_VIEW_ALL')")
    public PaymentSummary byRecord(@PathVariable UUID recordId) {
        UUID tenantId = TenantContext.requireTenantId();
        CrmRecord record = records.findByIdAndTenantIdAndSoftDeletedAtIsNull(recordId, tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", recordId));
        List<Payment> list = payments
                .findByTenantIdAndRecordIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(tenantId, recordId);
        BigDecimal received = sum(list, "RECEIVED");
        BigDecimal due = sum(list, "DUE");
        BigDecimal dealAmount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = dealAmount.subtract(received);
        return new PaymentSummary(dealAmount, received, due, outstanding,
                list.stream().map(PaymentView::of).toList());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('BILLING_MANAGE','PO_UPDATE')")
    public PaymentView create(@Valid @RequestBody PaymentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        records.findByIdAndTenantIdAndSoftDeletedAtIsNull(request.recordId(), tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", request.recordId()));
        String kind = request.kind() != null ? request.kind() : "RECEIVED";
        if (!KINDS.contains(kind)) {
            throw new BadRequestException("Invalid payment kind: " + kind);
        }
        Payment payment = new Payment(request.recordId(), kind, request.amount());
        if (request.currency() != null) {
            payment.setCurrency(request.currency());
        }
        payment.setDueDate(request.dueDate());
        payment.setReceivedDate(request.receivedDate());
        payment.setNote(request.note());
        payment = payments.save(payment);
        audit.record("CREATE", "PAYMENT", payment.getId());
        return PaymentView.of(payment);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('BILLING_MANAGE','PO_DELETE')")
    public void delete(@PathVariable UUID id) {
        Payment payment = payments.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Payment", id));
        payment.softDelete();
        payments.save(payment);
        audit.record("DELETE", "PAYMENT", id);
    }

    private BigDecimal sum(List<Payment> list, String kind) {
        return list.stream()
                .filter(p -> kind.equals(p.getKind()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
