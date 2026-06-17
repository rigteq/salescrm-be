package com.salescms.metadata;

import com.salescms.platform.auth.Tenant;
import com.salescms.platform.auth.TenantProvisionedEvent;
import com.salescms.platform.auth.TenantRepository;
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
