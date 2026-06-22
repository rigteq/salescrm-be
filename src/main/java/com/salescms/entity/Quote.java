package com.salescms.entity;

import com.salescms.entity.TenantOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotes")
public class Quote extends TenantOwnedEntity {

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "account_record_id")
    private UUID accountRecordId;

    @Column(name = "quote_number", nullable = false)
    private String quoteNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<QuoteLine> lines = new ArrayList<>();

    protected Quote() {
    }

    public Quote(String quoteNumber, String name) {
        this.quoteNumber = quoteNumber;
        this.name = name;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public UUID getAccountRecordId() {
        return accountRecordId;
    }

    public void setAccountRecordId(UUID accountRecordId) {
        this.accountRecordId = accountRecordId;
    }

    public String getQuoteNumber() {
        return quoteNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public List<QuoteLine> getLines() {
        return lines;
    }

    public void replaceLines(List<QuoteLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
        recomputeTotals();
    }

    public void recomputeTotals() {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (QuoteLine line : lines) {
            line.recompute();
            BigDecimal gross = line.getUnitPrice().multiply(line.getQuantity());
            sub = sub.add(gross);
            discount = discount.add(gross.subtract(line.getLineTotal()));
        }
        this.subtotal = sub;
        this.discountTotal = discount;
        this.total = sub.subtract(discount);
    }
}
