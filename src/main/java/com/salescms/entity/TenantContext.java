package com.salescms.entity;

import java.util.UUID;

/**
 * Holds the tenant and user identity for the current request thread.
 * Populated by {@code TenantContextFilter} from the verified JWT; cleared after each request.
 * Every repository query must be scoped by {@link #requireTenantId()}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId, UUID userId) {
        TENANT.set(tenantId);
        USER.set(userId);
    }

    public static UUID requireTenantId() {
        UUID id = TENANT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to current request");
        }
        return id;
    }

    public static UUID requireUserId() {
        UUID id = USER.get();
        if (id == null) {
            throw new IllegalStateException("No user bound to current request");
        }
        return id;
    }

    public static void clear() {
        TENANT.remove();
        USER.remove();
    }
}
