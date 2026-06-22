package com.salescms.controller;
import com.salescms.repository.TenantModuleRepository;
import com.salescms.entity.TenantModule;
import com.salescms.service.MetadataTemplateService;
import com.salescms.dto.MetadataDtos;

import com.salescms.exception.BadRequestException;
import com.salescms.entity.TenantContext;
import com.salescms.mapper.SalesCmsMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.salescms.dto.MetadataDtos.ModuleDto;
import static com.salescms.dto.MetadataDtos.ModuleUpdateRequest;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    private final TenantModuleRepository modules;
    private final MetadataTemplateService templates;
    private final SalesCmsMapper mapper;

    public ModuleController(TenantModuleRepository modules, MetadataTemplateService templates,
                            SalesCmsMapper mapper) {
        this.modules = modules;
        this.templates = templates;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED','SETTINGS_VIEW','PLATFORM_DASHBOARD_VIEW')")
    public List<ModuleDto> list() {
        return modules.findByTenantIdAndEnabledTrueOrderByDisplayOrderAsc(TenantContext.requireTenantId()).stream()
                .map(mapper::toModuleDto)
                .toList();
    }

    @PutMapping
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public List<ModuleDto> update(@RequestBody ModuleUpdateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.modules() == null) {
            throw new BadRequestException("modules is required");
        }
        for (ModuleDto item : request.modules()) {
            String key = normalizeModuleKey(item.moduleKey());
            TenantModule module = modules.findByTenantIdAndModuleKey(tenantId, key)
                    .orElseGet(() -> new TenantModule(tenantId, key,
                            fallback(item.singularLabel(), key), fallback(item.pluralLabel(), key),
                            item.icon(), item.displayOrder()));
            module.setEnabled(item.enabled());
            module.setSingularLabel(fallback(item.singularLabel(), key));
            module.setPluralLabel(fallback(item.pluralLabel(), key));
            module.setIcon(item.icon());
            module.setDisplayOrder(item.displayOrder());
            modules.save(module);
            templates.ensureModulePermissions(key);
        }
        return list();
    }

    private String normalizeModuleKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("moduleKey is required");
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private String fallback(String value, String key) {
        return value == null || value.isBlank() ? key : value.trim();
    }
}
