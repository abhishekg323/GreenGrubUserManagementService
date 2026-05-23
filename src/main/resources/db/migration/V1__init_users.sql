-- Baseline schema for the users table. Mirrors what Hibernate previously
-- generated from the User entity. Idempotent so it can be applied to
-- existing local databases that already had this table created by ddl-auto.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)   NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password      VARCHAR(255)  NOT NULL,
    phone_number  VARCHAR(10),
    address       VARCHAR(500),
    is_active     BOOLEAN       NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_user_email  ON users (email);
CREATE INDEX IF NOT EXISTS idx_user_role   ON users (role);
CREATE INDEX IF NOT EXISTS idx_user_active ON users (is_active);
