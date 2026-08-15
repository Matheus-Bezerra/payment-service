-- Local seed users/wallets to exercise POST /transfer.
-- Password hashes are BCrypt of the seed password documented in README.md.
-- Not a Flyway migration. Applied on boot when payment.seed.enabled=true.

INSERT INTO users (id, full_name, document_type, document_value, email, password_hash, type)
VALUES
    (
        '0190a1b2-c3d4-7000-8000-000000000004',
        'João Exemplo',
        'CPF',
        '39053344705',
        'joao.comum@example.com',
        '$2a$10$N1cYFREI55SM7KkMSv6ymetK4RtTQL3fPeGxWuTY1.6gAu.plk7Xq',
        'COMMON'
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000006',
        'Matheus',
        'CPF',
        '52998224725',
        'matheus@example.com',
        '$2a$10$N1cYFREI55SM7KkMSv6ymetK4RtTQL3fPeGxWuTY1.6gAu.plk7Xq',
        'COMMON'
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000015',
        'Loja Exemplo',
        'CNPJ',
        '11222333000181',
        'loja@example.com',
        '$2a$10$N1cYFREI55SM7KkMSv6ymetK4RtTQL3fPeGxWuTY1.6gAu.plk7Xq',
        'MERCHANT'
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000016',
        'Mercado Exemplo',
        'CNPJ',
        '99888777000166',
        'mercado@example.com',
        '$2a$10$N1cYFREI55SM7KkMSv6ymetK4RtTQL3fPeGxWuTY1.6gAu.plk7Xq',
        'MERCHANT'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallets (id, user_id, balance)
VALUES
    (
        '0190a1b2-c3d4-7000-8000-000000000104',
        '0190a1b2-c3d4-7000-8000-000000000004',
        50000.00
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000106',
        '0190a1b2-c3d4-7000-8000-000000000006',
        100000.00
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000115',
        '0190a1b2-c3d4-7000-8000-000000000015',
        10000.00
    ),
    (
        '0190a1b2-c3d4-7000-8000-000000000116',
        '0190a1b2-c3d4-7000-8000-000000000016',
        10000.00
    )
ON CONFLICT (id) DO NOTHING;
