CREATE TABLE transactions (
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    description  VARCHAR(255) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    category     VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transactions_dedupe UNIQUE (user_id, occurred_at, description, amount),
    CONSTRAINT chk_transactions_amount_nonzero CHECK (amount <> 0)
);

CREATE INDEX idx_transactions_user_occurred_at ON transactions (user_id, occurred_at);
