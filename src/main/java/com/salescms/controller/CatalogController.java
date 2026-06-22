package com.salescms.controller;
import com.salescms.repository.ProductRepository;
import com.salescms.entity.Product;
import com.salescms.repository.PriceBookRepository;
import com.salescms.repository.PriceBookEntryRepository;
import com.salescms.entity.PriceBookEntry;
import com.salescms.entity.PriceBook;

import com.salescms.service.AuditService;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    public record ProductRequest(@NotBlank String name, String sku, String description, Boolean active) {
    }

    public record ProductView(UUID id, String name, String sku, String description, boolean active) {

        static ProductView of(Product p) {
            return new ProductView(p.getId(), p.getName(), p.getSku(), p.getDescription(), p.isActive());
        }
    }

    public record PriceBookView(UUID id, String name, String currency, boolean isDefault, boolean active) {

        static PriceBookView of(PriceBook pb) {
            return new PriceBookView(pb.getId(), pb.getName(), pb.getCurrency(), pb.isDefault(), pb.isActive());
        }
    }

    public record PriceEntryRequest(@NotNull UUID productId, @NotNull BigDecimal unitPrice) {
    }

    public record PriceEntryView(UUID id, UUID productId, BigDecimal unitPrice) {

        static PriceEntryView of(PriceBookEntry e) {
            return new PriceEntryView(e.getId(), e.getProductId(), e.getUnitPrice());
        }
    }

    private final ProductRepository products;
    private final PriceBookRepository priceBooks;
    private final PriceBookEntryRepository entries;
    private final AuditService audit;

    public CatalogController(ProductRepository products, PriceBookRepository priceBooks,
                             PriceBookEntryRepository entries, AuditService audit) {
        this.products = products;
        this.priceBooks = priceBooks;
        this.entries = entries;
        this.audit = audit;
    }

    @GetMapping("/products")
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN','PO_CREATE','PO_UPDATE')")
    public Page<ProductView> listProducts(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size,
                                          @RequestParam(required = false) String q) {
        UUID tenantId = TenantContext.requireTenantId();
        var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Product> result = (q == null || q.isBlank())
                ? products.findByTenantIdAndSoftDeletedAtIsNull(tenantId, pageable)
                : products.findByTenantIdAndSoftDeletedAtIsNullAndNameContainingIgnoreCase(tenantId, q, pageable);
        return result.map(ProductView::of);
    }

    @PostMapping("/products")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_UPDATE')")
    public ProductView createProduct(@Valid @RequestBody ProductRequest request) {
        Product product = new Product(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        if (request.active() != null) {
            product.setActive(request.active());
        }
        product = products.save(product);
        audit.record("CREATE", "PRODUCT", product.getId());
        return ProductView.of(product);
    }

    @PutMapping("/products/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_UPDATE')")
    public ProductView updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        Product product = findProduct(id);
        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        if (request.active() != null) {
            product.setActive(request.active());
        }
        audit.record("UPDATE", "PRODUCT", id);
        return ProductView.of(products.save(product));
    }

    @DeleteMapping("/products/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_DELETE')")
    public void deleteProduct(@PathVariable UUID id) {
        Product product = findProduct(id);
        product.softDelete();
        products.save(product);
        audit.record("DELETE", "PRODUCT", id);
    }

    @GetMapping("/price-books")
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN','PO_CREATE','PO_UPDATE')")
    public List<PriceBookView> listPriceBooks() {
        return priceBooks.findByTenantIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
                        TenantContext.requireTenantId()).stream()
                .map(PriceBookView::of)
                .toList();
    }

    @GetMapping("/price-books/{id}/entries")
    @PreAuthorize("@permissionService.hasAnyPermission('PO_VIEW_ALL','PO_VIEW_OWN','PO_CREATE','PO_UPDATE')")
    public List<PriceEntryView> listEntries(@PathVariable UUID id) {
        requirePriceBook(id);
        return entries.findByTenantIdAndPriceBookId(TenantContext.requireTenantId(), id).stream()
                .map(PriceEntryView::of)
                .toList();
    }

    @PostMapping("/price-books/{id}/entries")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PO_UPDATE')")
    public PriceEntryView upsertEntry(@PathVariable UUID id, @Valid @RequestBody PriceEntryRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requirePriceBook(id);
        findProduct(request.productId());
        PriceBookEntry entry = entries
                .findByTenantIdAndPriceBookIdAndProductId(tenantId, id, request.productId())
                .map(e -> {
                    e.setUnitPrice(request.unitPrice());
                    return e;
                })
                .orElseGet(() -> new PriceBookEntry(tenantId, id, request.productId(), request.unitPrice()));
        entry = entries.save(entry);
        audit.record("PRICE_SET", "PRICE_BOOK", id);
        return PriceEntryView.of(entry);
    }

    private Product findProduct(UUID id) {
        return products.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    private PriceBook requirePriceBook(UUID id) {
        return priceBooks.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("PriceBook", id));
    }
}
