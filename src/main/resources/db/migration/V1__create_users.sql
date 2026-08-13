CREATE TABLE users (
    id              UUID         NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    document_type   VARCHAR(10)  NOT NULL,
    document_value  VARCHAR(14)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_document_value UNIQUE (document_value),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_document_type CHECK (document_type IN ('CPF', 'CNPJ')),
    CONSTRAINT ck_users_type CHECK (type IN ('COMMON', 'MERCHANT')),
    CONSTRAINT ck_users_document_value_length CHECK (
        (document_type = 'CPF'  AND char_length(document_value) = 11) OR
        (document_type = 'CNPJ' AND char_length(document_value) = 14)
    )
);

CREATE INDEX idx_users_type ON users (type);
