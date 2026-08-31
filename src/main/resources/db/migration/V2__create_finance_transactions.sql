CREATE TABLE finance_transactions (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    occurred_on DATE NOT NULL,
    type VARCHAR(10) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    memo TEXT NOT NULL
);

CREATE INDEX finance_transactions_user_date_idx ON finance_transactions (user_id, occurred_on);
