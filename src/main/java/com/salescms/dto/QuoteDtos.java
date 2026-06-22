package com.salescms.dto;
import com.salescms.entity.QuoteLine;
import com.salescms.entity.Quote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record QuoteLineRequest(
            UUID productId,
            @NotBlank String description,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            BigDecimal discountPct) {
    }

    public record QuoteRequest(
            @NotBlank String name,
            UUID recordId,
            UUID accountRecordId,
            String currency,
            LocalDate validUntil,
            List<QuoteLineRequest> lines) {
    }

    public record QuoteLineView(
            UUID id, UUID productId, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal discountPct, BigDecimal lineTotal, int position) {

        static QuoteLineView of(QuoteLine l) {
            return new QuoteLineView(l.getId(), l.getProductId(), l.getDescription(), l.getQuantity(),
                    l.getUnitPrice(), l.getDiscountPct(), l.getLineTotal(), l.getPosition());
        }
    }

    public record QuoteView(
            UUID id, String quoteNumber, String name, String status, String currency,
            UUID recordId, UUID accountRecordId,
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal total,
            LocalDate validUntil, List<QuoteLineView> lines,
            UUID ownerUserId, Instant createdAt, Instant updatedAt) {

        static QuoteView of(Quote q) {
            return new QuoteView(q.getId(), q.getQuoteNumber(), q.getName(), q.getStatus(), q.getCurrency(),
                    q.getRecordId(), q.getAccountRecordId(),
                    q.getSubtotal(), q.getDiscountTotal(), q.getTotal(),
                    q.getValidUntil(), q.getLines().stream().map(QuoteLineView::of).toList(),
                    q.getOwnerUserId(), q.getCreatedAt(), q.getUpdatedAt());
        }
    }
}
