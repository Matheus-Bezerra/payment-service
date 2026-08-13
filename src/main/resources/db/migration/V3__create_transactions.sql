CREATE TABLE transactions (
    id               UUID           NOT NULL,
    idempotency_key  VARCHAR(100)   NOT NULL,
    payer_id         UUID           NOT NULL,
    payee_id         UUID           NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    failure_reason   VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uk_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_payer_id FOREIGN KEY (payer_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_payee_id FOREIGN KEY (payee_id) REFERENCES users (id),
    CONSTRAINT ck_transactions_payer_ne_payee CHECK (payer_id <> payee_id),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_status CHECK (
        status IN ('IN_PROGRESS', 'AUTHORIZED', 'COMPLETED', 'FAILED', 'REVERSED')
    )
);

CREATE INDEX idx_transactions_payer_id ON transactions (payer_id);
CREATE INDEX idx_transactions_payee_id ON transactions (payee_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
CREATE INDEX idx_transactions_payer_created ON transactions (payer_id, created_at DESC);
