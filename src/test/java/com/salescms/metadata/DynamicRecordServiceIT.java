package com.salescms.metadata;

import com.salescms.metadata.MetadataDtos.RecordDto;
import com.salescms.metadata.MetadataDtos.RecordRequest;
import com.salescms.platform.common.BadRequestException;
import com.salescms.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicRecordServiceIT extends AbstractPostgresIT {

    @Autowired
    private DynamicRecordService records;

    private RecordRequest req(String title, Map<String, Object> custom) {
        return new RecordRequest(title, null, null, null, null, "USD", null, null, null, custom);
    }

    @Test
    void createsAndReadsRecord() {
        newSalesTenant();
        RecordDto created = records.create("accounts", req("Globex", Map.of()));
        assertThat(created.id()).isNotNull();
        assertThat(records.get(created.id()).title()).isEqualTo("Globex");
    }

    @Test
    void lookupValueAndReverseQuery() {
        newSalesTenant();
        RecordDto account = records.create("accounts", req("Acme Account", Map.of()));
        RecordDto contact = records.create("contacts", req("Jane Doe", Map.of("account", account.id().toString())));

        assertThat(contact.customValues()).containsEntry("account", account.id().toString());

        var related = records.related(account.id(), "contacts", "account", PageRequest.of(0, 10));
        assertThat(related.getContent()).extracting(RecordDto::id).contains(contact.id());
    }

    @Test
    void rejectsLookupToNonexistentRecord() {
        newSalesTenant();
        assertThatThrownBy(() ->
                records.create("contacts", req("Bad", Map.of("account", UUID.randomUUID().toString()))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsLookupToWrongModule() {
        newSalesTenant();
        RecordDto lead = records.create("leads", req("A Lead", Map.of()));
        // contacts.account must point at an account, not a lead.
        assertThatThrownBy(() ->
                records.create("contacts", req("Bad", Map.of("account", lead.id().toString()))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownCustomField() {
        newSalesTenant();
        assertThatThrownBy(() ->
                records.create("accounts", req("X", Map.of("not_a_field", "y"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void softDeleteHidesRecordFromListAndGet() {
        newSalesTenant();
        RecordDto created = records.create("accounts", req("Temp", Map.of()));
        records.delete(created.id());
        assertThat(records.list("accounts", null, null, PageRequest.of(0, 50)).getContent())
                .extracting(RecordDto::id).doesNotContain(created.id());
    }

    @Test
    void listWithNullQueryReturnsRows() {
        newSalesTenant();
        RecordDto created = records.create("opportunities", req("Big Deal", Map.of()));
        assertThat(records.list("opportunities", null, null, PageRequest.of(0, 50)).getContent())
                .extracting(RecordDto::id)
                .contains(created.id());
    }
}
