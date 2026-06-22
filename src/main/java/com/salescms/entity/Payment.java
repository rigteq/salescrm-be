package com.salescms.entity;

import com.salescms.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends TenantOwnedEntity {

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(nullable = false)
    private String kind = "RECEIVED";

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    private String note;

    protected Payment() {
    }

    public Payment(UUID recordId, String kind, BigDecimal amount) {
        this.recordId = recordId;
        this.kind = kind;
        this.amount = amount;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
