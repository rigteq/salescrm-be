package com.salescms.catalog;

import com.salescms.platform.auth.TenantProvisionedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Creates the default price book for every new tenant. */
@Component
public class CatalogProvisioning {

    private final PriceBookRepository priceBooks;

    public CatalogProvisioning(PriceBookRepository priceBooks) {
        this.priceBooks = priceBooks;
    }

    @EventListener
    public void onTenantProvisioned(TenantProvisionedEvent event) {
        priceBooks.save(new PriceBook("Standard Price Book", "USD", true));
    }
}
