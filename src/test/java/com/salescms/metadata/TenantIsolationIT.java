package com.salescms.metadata;

import com.salescms.dto.MetadataDtos.RecordDto;
import com.salescms.dto.MetadataDtos.RecordRequest;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import com.salescms.support.AbstractPostgresIT;
import com.salescms.service.DynamicRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the project's #1 hard rule: shared-schema tenancy must never leak rows
 * across tenants. Seeds two independent companies and asserts that neither can
 * read, list, or move the other's records. A regression here is a cross-tenant
 * data breach, so these assertions are deliberately blunt.
 */
class TenantIsolationIT extends AbstractPostgresIT {

    @Autowired
    private DynamicRecordService records;

    private RecordRequest req(String title) {
        return new RecordRequest(title, null, null, null, null, "USD", null, null, null, Map.of());
    }

    @Test
    void getDoesNotReturnAnotherTenantsRecord() {
        // Tenant A creates a record.
        UUID tenantA = newSalesTenant();
        RecordDto aRecord = records.create("accounts", req("Tenant A Account"));

        // Switch to a brand-new tenant B (newSalesTenant rebinds TenantContext).
        UUID tenantB = newSalesTenant();
        assertThat(tenantB).isNotEqualTo(tenantA);

        // B must not be able to fetch A's record by id.
        assertThatThrownBy(() -> records.get(aRecord.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listIsScopedToTheCurrentTenant() {
        newSalesTenant();
        RecordDto aRecord = records.create("accounts", req("A-only Account"));

        // A sees its own record.
        assertThat(records.list("accounts", null, null, PageRequest.of(0, 50)).getContent())
                .extracting(RecordDto::id)
                .contains(aRecord.id());

        // B's list of the same module is empty of A's rows.
        newSalesTenant();
        assertThat(records.list("accounts", null, null, PageRequest.of(0, 50)).getContent())
                .extracting(RecordDto::id)
                .doesNotContain(aRecord.id());
    }

    @Test
    void searchDoesNotMatchAnotherTenantsRecord() {
        newSalesTenant();
        records.create("accounts", req("Distinctive Search Token"));

        // From tenant B, searching the same term returns nothing of A's.
        newSalesTenant();
        assertThat(records.list("accounts", "Distinctive Search Token", null, PageRequest.of(0, 50)).getContent())
                .isEmpty();
    }

    @Test
    void cannotMutateAnotherTenantsRecord() {
        newSalesTenant();
        RecordDto aRecord = records.create("accounts", req("A Mutable Account"));

        newSalesTenant();
        // Update and delete are tenant-scoped lookups; A's id is invisible to B.
        assertThatThrownBy(() -> records.update(aRecord.id(), req("Hijacked")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> records.delete(aRecord.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void tenantContextIsRequiredForReads() {
        newSalesTenant();
        RecordDto aRecord = records.create("accounts", req("Needs Context"));

        // With no tenant bound, a read must not silently succeed.
        TenantContext.clear();
        assertThatThrownBy(() -> records.get(aRecord.id()))
                .isInstanceOf(RuntimeException.class);
    }
}


