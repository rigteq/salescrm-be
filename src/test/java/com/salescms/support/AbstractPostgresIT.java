package com.salescms.support;

import com.salescms.metadata.MetadataTemplateService;
import com.salescms.platform.auth.Tenant;
import com.salescms.platform.auth.TenantRepository;
import com.salescms.platform.auth.User;
import com.salescms.platform.auth.UserRepository;
import com.salescms.platform.rbac.RbacCatalog;
import com.salescms.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Base for DB integration tests: boots the full app against a throwaway
 * Postgres container (real schema via Flyway). {@code @ServiceConnection}
 * overrides the datasource, so the developer's configured DB (e.g. Neon) is
 * never touched. Skipped wholesale when Docker is unavailable.
 */
@SpringBootTest
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected TenantRepository tenants;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected MetadataTemplateService templates;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    /**
     * Creates an isolated company (tenant + owner), binds {@link TenantContext},
     * and provisions the generic-sales template (modules, pipelines, fields).
     * Each call uses fresh UUIDs, so the reused container needs no cleanup.
     */
    protected UUID newSalesTenant() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = tenants.save(new Tenant("Acme " + unique, "acme-" + unique));
        User owner = users.save(new User(tenant.getId(), unique + "@test.local",
                passwordEncoder.encode("password123"), "Test", "Owner", RbacCatalog.COMPANY_OWNER));
        TenantContext.set(tenant.getId(), owner.getId());
        templates.seedTemplates();
        templates.cloneTemplateForTenant("generic_sales", tenant.getId(), true);
        return tenant.getId();
    }
}
