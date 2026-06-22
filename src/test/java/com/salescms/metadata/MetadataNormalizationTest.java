package com.salescms.metadata;

import com.salescms.exception.BadRequestException;
import com.salescms.service.DynamicRecordService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure-function checks for module-key and field-type normalization, including the LOOKUP type. */
class MetadataNormalizationTest {

    @Test
    void normalizeKeyLowercasesAndSlugs() {
        assertThat(DynamicRecordService.normalizeKey("Opportunities")).isEqualTo("opportunities");
        assertThat(DynamicRecordService.normalizeKey("  Quotes ")).isEqualTo("quotes");
        assertThat(DynamicRecordService.normalizeKey("Real Estate")).isEqualTo("real_estate");
    }

    @Test
    void normalizeKeyRejectsBlank() {
        assertThatThrownBy(() -> DynamicRecordService.normalizeKey(" "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void normalizeFieldTypeAcceptsLookup() {
        assertThat(DynamicRecordService.normalizeFieldType("lookup")).isEqualTo("LOOKUP");
        assertThat(DynamicRecordService.normalizeFieldType("TEXT")).isEqualTo("TEXT");
        assertThat(DynamicRecordService.normalizeFieldType("user-picker")).isEqualTo("USER_PICKER");
    }

    @Test
    void normalizeFieldTypeRejectsUnknown() {
        assertThatThrownBy(() -> DynamicRecordService.normalizeFieldType("bogus"))
                .isInstanceOf(BadRequestException.class);
    }
}

