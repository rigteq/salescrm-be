package com.salescms.controller;
import com.salescms.repository.TenantModuleRepository;
import com.salescms.service.MetadataTemplateService;
import com.salescms.dto.MetadataDtos;
import com.salescms.repository.IndustryTemplateRepository;

import com.salescms.entity.Tenant;
import com.salescms.repository.TenantRepository;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.salescms.dto.MetadataDtos.SelectTemplateRequest;
import static com.salescms.dto.MetadataDtos.SetupStateDto;
import static com.salescms.dto.MetadataDtos.TemplateDto;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final TenantRepository tenants;
    private final IndustryTemplateRepository templates;
    private final TenantModuleRepository modules;
    private final MetadataTemplateService templateService;

    public SetupController(TenantRepository tenants, IndustryTemplateRepository templates,
                           TenantModuleRepository modules, MetadataTemplateService templateService) {
        this.tenants = tenants;
        this.templates = templates;
        this.modules = modules;
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('SETTINGS_VIEW','SETTINGS_UPDATE','LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public SetupStateDto state() {
        Tenant tenant = tenant();
        return new SetupStateDto(tenant.isSetupCompleted(), tenant.getSelectedTemplateKey(),
                templates.findByActiveTrueOrderByNameAsc().stream().map(TemplateDto::of).toList(),
                modules.findByTenantIdOrderByDisplayOrderAsc(tenant.getId()).stream()
                        .map(MetadataDtos.ModuleDto::of)
                        .toList());
    }

    @PostMapping("/select-template")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public SetupStateDto selectTemplate(@RequestBody SelectTemplateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        templateService.cloneTemplateForTenant(request.templateId(), tenantId, false);
        return state();
    }

    @PostMapping("/complete")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public SetupStateDto complete() {
        Tenant tenant = tenant();
        tenant.completeSetup();
        tenants.save(tenant);
        return state();
    }

    private Tenant tenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }
}
