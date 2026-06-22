package com.salescms.controller;
import com.salescms.repository.QuoteRepository;
import com.salescms.service.QuoteNumberService;
import com.salescms.entity.QuoteLine;
import com.salescms.dto.QuoteDtos;
import com.salescms.entity.Quote;

import com.salescms.service.AuditService;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.mapper.SalesCmsMapper;
import com.salescms.entity.TenantContext;
import jakarta.validation.Valid;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.salescms.dto.QuoteDtos.QuoteRequest;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private static final Set<String> STATUSES = Set.of("DRAFT", "SENT", "ACCEPTED", "REJECTED", "EXPIRED");

    private final QuoteRepository quotes;
    private final QuoteNumberService numbers;
    private final AuditService audit;
    private final SalesCmsMapper mapper;

    public QuoteController(QuoteRepository quotes, QuoteNumberService numbers, AuditService audit,
                           SalesCmsMapper mapper) {
        this.quotes = quotes;
        this.numbers = numbers;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN')")
    public Page<QuoteDtos.QuoteView> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return quotes.findByTenantIdAndSoftDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(mapper::toQuoteView);
    }

    @Transactional(readOnly = true)
    @GetMapping("/by-record/{recordId}")
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN')")
    public List<QuoteDtos.QuoteView> byRecord(@PathVariable UUID recordId) {
        return quotes.findByTenantIdAndRecordIdAndSoftDeletedAtIsNullOrderByCreatedAtDesc(
                        TenantContext.requireTenantId(), recordId).stream()
                .map(mapper::toQuoteView)
                .toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN')")
    public QuoteDtos.QuoteView get(@PathVariable UUID id) {
        return mapper.toQuoteView(find(id));
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_CREATE')")
    public QuoteDtos.QuoteView create(@Valid @RequestBody QuoteRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Quote quote = new Quote(numbers.nextNumber(tenantId), request.name());
        quote.setRecordId(request.recordId());
        quote.setAccountRecordId(request.accountRecordId());
        if (request.currency() != null) {
            quote.setCurrency(request.currency());
        }
        quote.setValidUntil(request.validUntil());
        quote = quotes.save(quote);
        applyLines(quote, request, tenantId);
        quote = quotes.save(quote);
        audit.record("CREATE", "QUOTE", quote.getId());
        return mapper.toQuoteView(quote);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_UPDATE')")
    public QuoteDtos.QuoteView update(@PathVariable UUID id, @Valid @RequestBody QuoteRequest request) {
        Quote quote = find(id);
        if (!"DRAFT".equals(quote.getStatus())) {
            throw new BadRequestException("Only draft quotes can be edited");
        }
        quote.setName(request.name());
        quote.setRecordId(request.recordId());
        quote.setAccountRecordId(request.accountRecordId());
        if (request.currency() != null) {
            quote.setCurrency(request.currency());
        }
        quote.setValidUntil(request.validUntil());
        applyLines(quote, request, quote.getTenantId());
        audit.record("UPDATE", "QUOTE", id);
        return mapper.toQuoteView(quotes.save(quote));
    }

    @PostMapping("/{id}/status")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_UPDATE')")
    public QuoteDtos.QuoteView setStatus(@PathVariable UUID id, @RequestParam String value) {
        if (!STATUSES.contains(value)) {
            throw new BadRequestException("Invalid quote status: " + value);
        }
        Quote quote = find(id);
        String from = quote.getStatus();
        quote.setStatus(value);
        audit.record("STATUS", "QUOTE", id, Map.of("from", from, "to", value));
        return mapper.toQuoteView(quotes.save(quote));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_DELETE')")
    public void delete(@PathVariable UUID id) {
        Quote quote = find(id);
        quote.softDelete();
        quotes.save(quote);
        audit.record("DELETE", "QUOTE", id);
    }

    private Quote find(UUID id) {
        return quotes.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Quote", id));
    }

    private void applyLines(Quote quote, QuoteRequest request, UUID tenantId) {
        List<QuoteLine> lines = new ArrayList<>();
        if (request.lines() != null) {
            int position = 0;
            for (var lr : request.lines()) {
                lines.add(new QuoteLine(tenantId, quote, lr.productId(), lr.description(),
                        lr.quantity(), lr.unitPrice(),
                        lr.discountPct() != null ? lr.discountPct() : BigDecimal.ZERO, position++));
            }
        }
        quote.replaceLines(lines);
    }
}
