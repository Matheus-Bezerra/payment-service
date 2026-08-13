CREATE TABLE notification_outbox (
    event_id        UUID         NOT NULL,
    transaction_id  UUID         NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT pk_notification_outbox PRIMARY KEY (event_id),
    CONSTRAINT uk_notification_outbox_transaction_id UNIQUE (transaction_id),
    CONSTRAINT fk_notification_outbox_transaction_id
        FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT ck_notification_outbox_status CHECK (
        status IN ('PENDING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_notification_outbox_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (status, created_at)
    WHERE status = 'PENDING';
