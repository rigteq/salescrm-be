package com.salescms.platform.settings;

import com.salescms.platform.tenancy.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final String DEFAULT_TEMPLATE =
            "Hi {name}, thanks for your interest! I'd love to understand your "
            + "requirements and share how we can help. When is a good time to talk?";

    public record SettingsView(String whatsappTemplate, String defaultCurrency) {
    }

    public record SettingsRequest(String whatsappTemplate, String defaultCurrency) {
    }

    private final TenantSettingsRepository repository;

    public SettingsController(TenantSettingsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('SETTINGS_VIEW')")
    public SettingsView get() {
        TenantSettings settings = loadOrCreate();
        return new SettingsView(settings.getWhatsappTemplate(), settings.getDefaultCurrency());
    }

    @PutMapping
    @Transactional
    @PreAuthorize("@permissionService.hasAnyPermission('SETTINGS_UPDATE','WHATSAPP_TEMPLATE_MANAGE')")
    public SettingsView update(@RequestBody SettingsRequest request) {
        TenantSettings settings = loadOrCreate();
        settings.setWhatsappTemplate(request.whatsappTemplate());
        if (request.defaultCurrency() != null && !request.defaultCurrency().isBlank()) {
            settings.setDefaultCurrency(request.defaultCurrency());
        }
        settings.touch();
        repository.save(settings);
        return new SettingsView(settings.getWhatsappTemplate(), settings.getDefaultCurrency());
    }

    private TenantSettings loadOrCreate() {
        UUID tenantId = TenantContext.requireTenantId();
        return repository.findById(tenantId).orElseGet(() -> {
            TenantSettings settings = new TenantSettings(tenantId);
            settings.setWhatsappTemplate(DEFAULT_TEMPLATE);
            return repository.save(settings);
        });
    }
}
