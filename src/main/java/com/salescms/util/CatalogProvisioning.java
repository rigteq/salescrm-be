package com.salescms.util;
import com.salescms.repository.PriceBookRepository;
import com.salescms.entity.PriceBook;

import com.salescms.event.TenantProvisionedEvent;
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
