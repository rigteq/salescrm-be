package com.salescms.quote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_lines")
public class QuoteLine {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "discount_pct", nullable = false)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected QuoteLine() {
    }

    public QuoteLine(UUID tenantId, Quote quote, UUID productId, String description,
                     BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, int position) {
        this.tenantId = tenantId;
        this.quote = quote;
        this.productId = productId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountPct = discountPct != null ? discountPct : BigDecimal.ZERO;
        this.position = position;
        recompute();
    }

    void recompute() {
        BigDecimal gross = unitPrice.multiply(quantity);
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        this.lineTotal = gross.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountPct() {
        return discountPct;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public int getPosition() {
        return position;
    }
}
