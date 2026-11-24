-- ============================================================================
-- Black-Coat Flyway Migration V4: Seed Anonymous/Guest Demo User
-- Guarantees foreign key integrity for unauthenticated playground requests
-- ============================================================================

INSERT INTO users (id, email, password_hash, display_name, lang_pref)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'guest@blackcoat.internal',
    '$argon2id$v=19$m=65536,t=3,p=1$ZGVtb3NhbHQxMjM0NTY3OA$e8fP3K8fV4i2G7Q2rN1V5s0X6bZ8wY4T3kP9mQ1aBc0',
    'Guest Visitor',
    'en'
)
ON CONFLICT (id) DO NOTHING;
