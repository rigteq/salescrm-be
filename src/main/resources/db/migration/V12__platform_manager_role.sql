-- Allow the new PLATFORM_MANAGER role code on the denormalized users.role column.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN (
        'PLATFORM_OWNER',
        'PLATFORM_MANAGER',
        'COMPANY_OWNER',
        'COMPANY_ADMIN',
        'SALES_MANAGER',
        'SALES_EXECUTIVE',
        'FINANCE',
        'VIEWER'
    ));
