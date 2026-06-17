package com.salescms.platform.rbac;

import com.salescms.platform.auth.Tenant;
import com.salescms.platform.auth.TenantRepository;
import com.salescms.platform.auth.User;
import com.salescms.platform.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class PlatformAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminSeeder.class);

    private final UserRepository users;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminSeeder(UserRepository users, TenantRepository tenants, 
                               RoleRepository roles, UserRoleRepository userRoles, 
                               PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tenants = tenants;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminEmail = "pa@crm.com";
        String adminPassword = "pass123";

        Tenant platformTenant = tenants.findBySlug("platform")
                .orElseGet(() -> tenants.save(new Tenant("SalesCMS Platform", "platform")));

        Role platformRole = roles.findByCodeAndCompanyIdIsNullAndDeletedFalse(RbacCatalog.PLATFORM_OWNER)
                .orElseThrow();

        if (users.findByEmail(adminEmail).isEmpty()) {
            User admin = new User(platformTenant.getId(), adminEmail,
                    passwordEncoder.encode(adminPassword), "Platform", "Admin",
                    RbacCatalog.PLATFORM_OWNER);
            admin.setStatus("ACTIVE");
            users.save(admin);
            
            userRoles.save(new UserRole(admin.getId(), platformRole.getId(), platformTenant.getId(), true));
            log.info("Created platform admin account: {}", adminEmail);
        } else {
            log.info("Platform admin account {} already exists.", adminEmail);
        }
    }
}
