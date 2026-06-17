package com.salescms.platform.demo;

import com.salescms.metadata.MetadataTemplateService;
import com.salescms.platform.rbac.RbacCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in demo data generator. Runs only when {@code SALESCMS_SEED_DEMO=true}
 * and only once (skips if demo companies already exist). Builds a full
 * org hierarchy — platform owners/managers, 20 companies each with company
 * admins and a 10–100 person team — plus representative CRM records, products,
 * quotes, tasks and comments for every company.
 *
 * <p>All inserts go through JDBC for speed; provisioning of modules/pipelines/
 * fields reuses {@link MetadataTemplateService}. Every demo user shares one
 * bcrypt hash (password: {@code Demo!2345}) so seeding stays fast.
 */
@Component
@Order(300)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_PASSWORD = "Demo!2345";
    private static final String DOMAIN = "demo.salescms.local";
    private static final int COMPANY_COUNT = 20;
    private static final int PLATFORM_OWNERS = 3;
    private static final int PLATFORM_MANAGERS = 5;
    private static final String[] CURRENCIES = {"USD", "INR", "EUR", "GBP", "AUD", "CAD"};

    private static final String[] FIRST = {
            "Aarav", "Diya", "Liam", "Noah", "Olivia", "Emma", "Ananya", "Vihaan", "Sophia", "Mateo",
            "Aisha", "Kabir", "Mia", "Lucas", "Isabella", "Reyansh", "Ava", "Ethan", "Sara", "Arjun",
            "Maya", "Leo", "Zoe", "Ishaan", "Nora", "Ryan", "Priya", "Ben", "Chloe", "Dev"};
    private static final String[] LAST = {
            "Sharma", "Patel", "Smith", "Johnson", "Kumar", "Williams", "Singh", "Brown", "Gupta", "Jones",
            "Mehta", "Garcia", "Reddy", "Miller", "Khan", "Davis", "Nair", "Wilson", "Bose", "Taylor"};
    private static final String[] COMPANY_NAMES = {
            "Nimbus", "Apex", "Vertex", "Quanta", "Lumen", "Horizon", "Catalyst", "Summit", "Pinnacle", "Helio",
            "Orbit", "Atlas", "Beacon", "Cobalt", "Delta", "Ember", "Fathom", "Granite", "Harbor", "Ivory"};
    private static final String[] COMPANY_SUFFIX = {"Labs", "Systems", "Group", "Technologies", "Solutions", "Works"};
    private static final String[] ACCOUNT_NAMES = {
            "Globex", "Initech", "Umbrella", "Soylent", "Hooli", "Stark Industries", "Wayne Enterprises",
            "Wonka", "Acme", "Cyberdyne", "Tyrell", "Aperture", "Massive Dynamic", "Vandelay", "Pied Piper"};
    private static final String[] PRODUCTS = {
            "Starter Plan", "Growth Plan", "Enterprise Plan", "Onboarding Package", "Premium Support",
            "Analytics Add-on", "API Access", "Training Workshop"};
    private static final String[] LEAD_SOURCES = {"Web", "Referral", "Event", "Cold Call", "Partner", "Inbound"};
    private static final String[] LEAD_STATUSES = {"NEW", "WORKING", "QUALIFIED", "UNQUALIFIED"};
    private static final String[] TEAM_ROLES = {
            RbacCatalog.SALES_MANAGER, RbacCatalog.SALES_EXECUTIVE, RbacCatalog.SALES_EXECUTIVE,
            RbacCatalog.SALES_EXECUTIVE, RbacCatalog.FINANCE, RbacCatalog.VIEWER};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MetadataTemplateService templates;
    private final boolean enabled;
    private final Random random = new Random(42);

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          MetadataTemplateService templates,
                          @org.springframework.beans.factory.annotation.Value("${salescms.seed-demo:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.templates = templates;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Integer existing = jdbc.queryForObject(
                "select count(*) from tenants where slug like 'demo-co-%'", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Demo data already present ({} demo companies); skipping seed.", existing);
            return;
        }
        log.info("SALESCMS_SEED_DEMO is on — generating demo data. This can take a few minutes…");
        templates.seedTemplates();
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        Map<String, UUID> roleIds = loadRoleIds();

        seedPlatformStaff(hash, roleIds);
        int totalUsers = PLATFORM_OWNERS + PLATFORM_MANAGERS;
        for (int c = 1; c <= COMPANY_COUNT; c++) {
            totalUsers += seedCompany(c, hash, roleIds);
            log.info("Seeded demo company {}/{}", c, COMPANY_COUNT);
        }
        log.info("Demo data complete: {} companies, ~{} users. Login with any seeded email / password '{}'.",
                COMPANY_COUNT, totalUsers, DEMO_PASSWORD);
    }

    // ---- platform tier -------------------------------------------------------

    private void seedPlatformStaff(String hash, Map<String, UUID> roleIds) {
        UUID existing = jdbc.query("select id from tenants where slug='platform' limit 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        UUID platformTenant = existing != null ? existing : insertTenant("SalesCMS Platform", "platform");
        for (int i = 1; i <= PLATFORM_OWNERS; i++) {
            createUser(platformTenant, "platform-owner-" + i + "@" + DOMAIN,
                    pick(FIRST), "Owner", RbacCatalog.PLATFORM_OWNER, hash, roleIds);
        }
        for (int i = 1; i <= PLATFORM_MANAGERS; i++) {
            createUser(platformTenant, "platform-manager-" + i + "@" + DOMAIN,
                    pick(FIRST), "Manager", RbacCatalog.PLATFORM_MANAGER, hash, roleIds);
        }
    }

    // ---- company tier --------------------------------------------------------

    private int seedCompany(int index, String hash, Map<String, UUID> roleIds) {
        String companyName = COMPANY_NAMES[(index - 1) % COMPANY_NAMES.length] + " "
                + COMPANY_SUFFIX[random.nextInt(COMPANY_SUFFIX.length)];
        String slug = "demo-co-" + index;
        UUID tenantId = insertTenant(companyName, slug);
        String currency = CURRENCIES[index % CURRENCIES.length];
        jdbc.update("insert into tenant_settings (tenant_id, default_currency, updated_at) values (?,?,now()) "
                + "on conflict (tenant_id) do update set default_currency=excluded.default_currency", tenantId, currency);

        // Modules, pipelines, fields, permissions (marks template + setup complete).
        templates.cloneTemplateForTenant("generic_sales", tenantId, true);

        // People: 1 owner, 2-3 admins, 10-100 team members.
        String domain = slug + "." + DOMAIN;
        UUID owner = createUser(tenantId, "owner@" + domain, pick(FIRST), pick(LAST),
                RbacCatalog.COMPANY_OWNER, hash, roleIds);
        List<UUID> assignees = new ArrayList<>();
        assignees.add(owner);
        int admins = 2 + random.nextInt(2);
        for (int i = 1; i <= admins; i++) {
            assignees.add(createUser(tenantId, "admin" + i + "@" + domain, pick(FIRST), pick(LAST),
                    RbacCatalog.COMPANY_ADMIN, hash, roleIds));
        }
        int team = 10 + random.nextInt(91);
        List<Object[]> userRows = new ArrayList<>();
        List<Object[]> roleRows = new ArrayList<>();
        for (int i = 1; i <= team; i++) {
            UUID uid = UUID.randomUUID();
            String roleCode = TEAM_ROLES[random.nextInt(TEAM_ROLES.length)];
            String first = pick(FIRST);
            String last = pick(LAST);
            userRows.add(new Object[]{uid, tenantId,
                    "member" + i + "@" + domain, hash, first, last, roleCode});
            roleRows.add(new Object[]{UUID.randomUUID(), uid, roleIds.get(roleCode), tenantId, true});
            assignees.add(uid);
        }
        jdbc.batchUpdate("insert into users (id, tenant_id, email, password_hash, first_name, last_name, role, status, is_deleted) "
                + "values (?,?,?,?,?,?,?,'ACTIVE',false)", userRows);
        jdbc.batchUpdate("insert into user_roles (id, user_id, role_id, company_id, primary_role) values (?,?,?,?,?)", roleRows);

        seedRecords(tenantId, currency, assignees);
        return 1 + admins + team;
    }

    // ---- CRM records, products, quotes, tasks, comments ----------------------

    private void seedRecords(UUID tenantId, String currency, List<UUID> users) {
        UUID pipelineId = jdbc.queryForObject(
                "select id from pipelines where tenant_id=? and module_key='opportunities' and soft_deleted_at is null "
                        + "order by is_default desc limit 1", UUID.class, tenantId);
        List<UUID> stages = jdbc.queryForList(
                "select id from stages where tenant_id=? and pipeline_id=? order by position", UUID.class, tenantId, pipelineId);
        List<String> stageStatuses = jdbc.queryForList(
                "select case when is_won then 'WON' when is_lost then 'LOST' else 'OPEN' end "
                        + "from stages where tenant_id=? and pipeline_id=? order by position", String.class, tenantId, pipelineId);
        UUID contactAccountField = fieldId(tenantId, "contacts", "account");
        UUID oppAccountField = fieldId(tenantId, "opportunities", "account");

        // Products
        List<UUID> productIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID pid = UUID.randomUUID();
            String name = PRODUCTS[i % PRODUCTS.length];
            jdbc.update("insert into products (id, tenant_id, name, sku, description, active, owner_user_id) "
                    + "values (?,?,?,?,?,true,?)", pid, tenantId, name, "SKU-" + (1000 + i),
                    name + " — demo product", owner(users));
            productIds.add(pid);
        }

        // Accounts
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String name = ACCOUNT_NAMES[i % ACCOUNT_NAMES.length] + " " + (char) ('A' + i);
            UUID rid = insertRecord(tenantId, "accounts", null, null, owner(users), name, "ACTIVE", null, currency);
            putText(tenantId, rid, "industry", List.of("SaaS", "Retail", "Finance", "Healthcare").get(i % 4));
            accounts.add(rid);
        }

        // Contacts (linked to an account)
        for (int i = 0; i < 14; i++) {
            String name = pick(FIRST) + " " + pick(LAST);
            UUID rid = insertRecord(tenantId, "contacts", null, null, owner(users), name, "ACTIVE", null, currency);
            putLookup(tenantId, rid, contactAccountField, "account", accounts.get(i % accounts.size()));
            putText(tenantId, rid, "email", name.toLowerCase().replace(' ', '.') + "@example.com");
        }

        // Leads
        for (int i = 0; i < 16; i++) {
            String name = pick(FIRST) + " " + pick(LAST);
            insertRecord(tenantId, "leads", null, null, owner(users), name,
                    LEAD_STATUSES[i % LEAD_STATUSES.length], LEAD_SOURCES[i % LEAD_SOURCES.length], currency);
        }

        // Opportunities (staged), with tasks/comments/quotes on some
        for (int i = 0; i < 12; i++) {
            int s = random.nextInt(stages.size());
            BigDecimal amount = BigDecimal.valueOf((5 + random.nextInt(95)) * 1000L);
            UUID account = accounts.get(i % accounts.size());
            String title = "Deal — " + ACCOUNT_NAMES[i % ACCOUNT_NAMES.length];
            UUID owner = owner(users);
            UUID rid = insertRecord(tenantId, "opportunities", pipelineId, stages.get(s), owner, title,
                    stageStatuses.get(s), null, currency, amount);
            putLookup(tenantId, rid, oppAccountField, "account", account);
            if (i % 3 == 0) {
                seedTask(tenantId, rid, owner, "Follow up on " + title);
            }
            if (i % 4 == 0) {
                seedComment(tenantId, rid, owner, "Sent proposal, awaiting feedback.");
            }
            if (i % 3 == 1) {
                seedQuote(tenantId, currency, owner, rid, account, productIds);
            }
        }
    }

    private void seedQuote(UUID tenantId, String currency, UUID owner, UUID oppRecord, UUID accountRecord,
                           List<UUID> productIds) {
        UUID quoteId = UUID.randomUUID();
        long seq = jdbc.queryForObject(
                "select coalesce(count(*),0)+1 from quotes where tenant_id=?", Long.class, tenantId);
        String number = String.format("Q-%04d", seq);
        BigDecimal subtotal = BigDecimal.ZERO;
        List<Object[]> lines = new ArrayList<>();
        int lineCount = 1 + random.nextInt(3);
        for (int i = 0; i < lineCount; i++) {
            BigDecimal qty = BigDecimal.valueOf(1 + random.nextInt(5));
            BigDecimal price = BigDecimal.valueOf((1 + random.nextInt(20)) * 100L);
            BigDecimal lineTotal = qty.multiply(price);
            subtotal = subtotal.add(lineTotal);
            lines.add(new Object[]{UUID.randomUUID(), tenantId, quoteId, productIds.get(i % productIds.size()),
                    PRODUCTS[i % PRODUCTS.length], qty, price, BigDecimal.ZERO, lineTotal, i});
        }
        BigDecimal total = subtotal.setScale(2, RoundingMode.HALF_UP);
        jdbc.update("insert into quotes (id, tenant_id, record_id, account_record_id, quote_number, name, status, "
                + "currency, subtotal, discount_total, total, owner_user_id, version) "
                + "values (?,?,?,?,?,?, 'DRAFT', ?,?,0,?,?,0)",
                quoteId, tenantId, oppRecord, accountRecord, number, "Quote " + number, currency, total, total, owner);
        jdbc.batchUpdate("insert into quote_lines (id, tenant_id, quote_id, product_id, description, quantity, "
                + "unit_price, discount_pct, line_total, position) values (?,?,?,?,?,?,?,?,?,?)", lines);
    }

    private void seedTask(UUID tenantId, UUID recordId, UUID owner, String title) {
        jdbc.update("insert into tasks (id, tenant_id, title, priority, status, related_object_type, "
                + "related_object_id, owner_user_id, version) values (?,?,?, 'NORMAL', 'OPEN', 'CRM_RECORD', ?,?,0)",
                UUID.randomUUID(), tenantId, title, recordId, owner);
    }

    private void seedComment(UUID tenantId, UUID recordId, UUID author, String body) {
        jdbc.update("insert into comments (id, tenant_id, related_object_type, related_object_id, body, author_user_id) "
                + "values (?,?, 'CRM_RECORD', ?,?,?)", UUID.randomUUID(), tenantId, recordId, body, author);
    }

    // ---- low-level helpers ---------------------------------------------------

    private UUID insertRecord(UUID tenantId, String module, UUID pipelineId, UUID stageId, UUID owner,
                              String title, String status, String source, String currency) {
        return insertRecord(tenantId, module, pipelineId, stageId, owner, title, status, source, currency, null);
    }

    private UUID insertRecord(UUID tenantId, String module, UUID pipelineId, UUID stageId, UUID owner,
                              String title, String status, String source, String currency, BigDecimal amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into crm_records (id, tenant_id, module_key, pipeline_id, stage_id, owner_user_id, "
                + "title, status, source, amount, currency, created_by, updated_by, version) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id, tenantId, module, pipelineId, stageId, owner, title, status, source, amount, currency, owner, owner);
        return id;
    }

    private void putLookup(UUID tenantId, UUID recordId, UUID fieldId, String fieldKey, UUID target) {
        if (fieldId == null) {
            return;
        }
        jdbc.update("insert into crm_record_custom_values (id, tenant_id, record_id, field_id, field_key, value_record_id) "
                + "values (?,?,?,?,?,?) on conflict (record_id, field_key) do update set value_record_id=excluded.value_record_id",
                UUID.randomUUID(), tenantId, recordId, fieldId, fieldKey, target);
    }

    private void putText(UUID tenantId, UUID recordId, String fieldKey, String value) {
        UUID fieldId = jdbc.query("select id from custom_fields where tenant_id=? and field_key=? and soft_deleted_at is null limit 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, fieldKey);
        if (fieldId == null) {
            return;
        }
        jdbc.update("insert into crm_record_custom_values (id, tenant_id, record_id, field_id, field_key, value_text) "
                + "values (?,?,?,?,?,?) on conflict (record_id, field_key) do update set value_text=excluded.value_text",
                UUID.randomUUID(), tenantId, recordId, fieldId, fieldKey, value);
    }

    private UUID fieldId(UUID tenantId, String module, String fieldKey) {
        return jdbc.query("select id from custom_fields where tenant_id=? and module_key=? and field_key=? and soft_deleted_at is null limit 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, module, fieldKey);
    }

    private UUID insertTenant(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenants (id, name, slug, status) values (?,?,?, 'ACTIVE')", id, name, slug);
        return id;
    }

    private UUID createUser(UUID tenantId, String email, String first, String last, String roleCode,
                            String hash, Map<String, UUID> roleIds) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, tenant_id, email, password_hash, first_name, last_name, role, status, is_deleted) "
                + "values (?,?,?,?,?,?,?, 'ACTIVE', false)", id, tenantId, email.toLowerCase(), hash, first, last, roleCode);
        jdbc.update("insert into user_roles (id, user_id, role_id, company_id, primary_role) values (?,?,?,?,true)",
                UUID.randomUUID(), id, roleIds.get(roleCode), tenantId);
        return id;
    }

    private Map<String, UUID> loadRoleIds() {
        Map<String, UUID> ids = new ConcurrentHashMap<>();
        jdbc.query("select code, id from roles where company_id is null and is_deleted=false",
                rs -> { ids.put(rs.getString("code"), rs.getObject("id", UUID.class)); });
        return ids;
    }

    private UUID owner(List<UUID> users) {
        return users.get(random.nextInt(users.size()));
    }

    private String pick(String[] pool) {
        return pool[random.nextInt(pool.length)];
    }
}
