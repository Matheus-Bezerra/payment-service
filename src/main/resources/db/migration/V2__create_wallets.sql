CREATE TABLE wallets (
    id          UUID           NOT NULL,
    user_id     UUID           NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT fk_wallets_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0)
);
