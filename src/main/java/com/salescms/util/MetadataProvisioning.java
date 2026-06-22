package com.salescms.util;
import com.salescms.repository.TenantModuleRepository;
import com.salescms.service.MetadataTemplateService;

import com.salescms.entity.Tenant;
import com.salescms.event.TenantProvisionedEvent;
import com.salescms.repository.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class MetadataProvisioning implements ApplicationRunner {

    private final MetadataTemplateService templates;
    private final TenantRepository tenants;
    private final TenantModuleRepository modules;

    public MetadataProvisioning(MetadataTemplateService templates, TenantRepository tenants,
                                TenantModuleRepository modules) {
        this.templates = templates;
        this.tenants = tenants;
        this.modules = modules;
    }

    @Override
    public void run(ApplicationArguments args) {
        templates.seedTemplates();
        for (Tenant tenant : tenants.findAll()) {
            if (tenant.getSelectedTemplateKey() == null) {
                templates.cloneTemplateForTenant("generic_sales", tenant.getId(), true);
            }
        }
    }

    @EventListener
    public void onTenantProvisioned(TenantProvisionedEvent event) {
        templates.seedTemplates();
        if (modules.countByTenantId(event.tenantId()) == 0) {
            templates.cloneTemplateForTenant("generic_sales", event.tenantId(), false);
        }
    }
}
