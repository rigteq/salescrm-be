package com.salescms.controller;
import com.salescms.repository.TenantModuleRepository;
import com.salescms.entity.TenantModule;
import com.salescms.dto.MetadataDtos;
import com.salescms.service.DynamicRecordService;
import com.salescms.repository.CustomFieldRepository;
import com.salescms.entity.CustomField;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salescms.mapper.SalesCmsMapper;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.salescms.dto.MetadataDtos.CustomFieldDto;
import static com.salescms.dto.MetadataDtos.CustomFieldRequest;

@RestController
@RequestMapping("/api/custom-fields")
public class CustomFieldController {

    private final CustomFieldRepository fields;
    private final TenantModuleRepository modules;
    private final ObjectMapper objectMapper;
    private final SalesCmsMapper mapper;

    public CustomFieldController(CustomFieldRepository fields, TenantModuleRepository modules,
                                 ObjectMapper objectMapper, SalesCmsMapper mapper) {
        this.fields = fields;
        this.modules = modules;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED','SETTINGS_VIEW')")
    public List<CustomFieldDto> list(@RequestParam(required = false) String moduleKey) {
        UUID tenantId = TenantContext.requireTenantId();
        if (moduleKey == null || moduleKey.isBlank()) {
            return fields.findByTenantIdAndSoftDeletedAtIsNullOrderByModuleKeyAscDisplayOrderAsc(tenantId).stream()
                    .map(mapper::toCustomFieldDto)
                    .toList();
        }
        String key = DynamicRecordService.normalizeKey(moduleKey);
        return fields.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByDisplayOrderAsc(tenantId, key).stream()
                .map(mapper::toCustomFieldDto)
                .toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public CustomFieldDto create(@RequestBody CustomFieldRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String moduleKey = requireModule(tenantId, request.moduleKey());
        String fieldKey = normalizeFieldKey(request.fieldKey());
        if (fields.findByTenantIdAndModuleKeyAndFieldKeyAndSoftDeletedAtIsNull(tenantId, moduleKey, fieldKey).isPresent()) {
            throw new BadRequestException("Field already exists: " + fieldKey);
        }
        CustomField field = new CustomField(tenantId, moduleKey, fieldKey,
                requiredText(request.label(), "label"),
                DynamicRecordService.normalizeFieldType(request.fieldType()));
        apply(field, request);
        return mapper.toCustomFieldDto(fields.save(field));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public CustomFieldDto update(@PathVariable UUID id, @RequestBody CustomFieldRequest request) {
        CustomField field = fields.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("CustomField", id));
        if (!field.getModuleKey().equals(DynamicRecordService.normalizeKey(request.moduleKey()))) {
            throw new BadRequestException("Custom field module cannot be changed");
        }
        field.setFieldKey(normalizeFieldKey(request.fieldKey()));
        field.setLabel(requiredText(request.label(), "label"));
        field.setFieldType(DynamicRecordService.normalizeFieldType(request.fieldType()));
        apply(field, request);
        return mapper.toCustomFieldDto(fields.save(field));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void delete(@PathVariable UUID id) {
        CustomField field = fields.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("CustomField", id));
        if (field.isSystemField()) {
            throw new BadRequestException("System fields cannot be deleted");
        }
        field.softDelete();
        fields.save(field);
    }

    private void apply(CustomField field, CustomFieldRequest request) {
        field.setOptionsJson(jsonOrDefault(request.optionsJson(), "{}"));
        field.setValidationJson(jsonOrDefault(request.validationJson(), "{}"));
        field.setVisibilityRulesJson(jsonOrDefault(request.visibilityRulesJson(), "{}"));
        field.setRequired(request.required());
        field.setDisplayOrder(request.displayOrder());
    }

    private String requireModule(UUID tenantId, String rawModuleKey) {
        String moduleKey = DynamicRecordService.normalizeKey(rawModuleKey);
        TenantModule module = modules.findByTenantIdAndModuleKey(tenantId, moduleKey)
                .orElseThrow(() -> new NotFoundException("TenantModule", moduleKey));
        if (!module.isEnabled()) {
            throw new BadRequestException("Module is disabled: " + moduleKey);
        }
        return moduleKey;
    }

    private String normalizeFieldKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("fieldKey is required");
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }

    private String jsonOrDefault(String value, String defaultValue) {
        String candidate = value == null || value.isBlank() ? defaultValue : value;
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid JSON");
        }
    }
}
