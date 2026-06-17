package com.salescms.catalog;

import com.salescms.platform.common.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_books")
public class PriceBook extends TenantOwnedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean active = true;

    protected PriceBook() {
    }

    public PriceBook(String name, String currency, boolean isDefault) {
        this.name = name;
        this.currency = currency;
        this.isDefault = isDefault;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
