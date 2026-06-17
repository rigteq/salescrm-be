package com.salescms.metadata;

import com.salescms.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataTemplateServiceIT extends AbstractPostgresIT {

    @Autowired
    private CustomFieldRepository fields;
    @Autowired
    private TenantModuleRepository modules;

    @Test
    void clonesGenericSalesWithLookupAndStandardFields() {
        UUID tenantId = newSalesTenant();

        assertThat(modules.findByTenantIdAndModuleKey(tenantId, "opportunities")).isPresent();

        var contactFields = fields.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(
                tenantId, "contacts");
        assertThat(contactFields)
                .anyMatch(f -> f.getFieldKey().equals("account") && f.getFieldType().equals("LOOKUP"))
                .anyMatch(f -> f.getFieldKey().equals("email"));
    }

    @Test
    void reCloneIsIdempotent() {
        UUID tenantId = newSalesTenant();
        templates.cloneTemplateForTenant("generic_sales", tenantId, true);

        long industry = fields.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(
                        tenantId, "accounts").stream()
                .filter(f -> f.getFieldKey().equals("industry")).count();
        assertThat(industry).isEqualTo(1);
    }
}
