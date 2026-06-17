package com.salescms.platform.auth;

import java.util.UUID;

/**
 * Published once when a new tenant signs up, with TenantContext already bound.
 * Modules listen to provision their tenant defaults (pipeline, price book, ...).
 */
public record TenantProvisionedEvent(UUID tenantId, UUID adminUserId) {
}
