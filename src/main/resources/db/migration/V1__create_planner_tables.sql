CREATE TABLE user_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    available_cash BIGINT NOT NULL CHECK (available_cash >= 0),
    monthly_income BIGINT NOT NULL CHECK (monthly_income >= 0),
    age INTEGER NOT NULL CHECK (age >= 0),
    region VARCHAR(100) NOT NULL,
    employment_status VARCHAR(30) NOT NULL,
    monthly_income_for_policy BIGINT NOT NULL CHECK (monthly_income_for_policy >= 0),
    target_job_month VARCHAR(7) NOT NULL
);

CREATE TABLE job_events (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_date DATE NOT NULL,
    expected_cost BIGINT NOT NULL CHECK (expected_cost >= 0),
    memo TEXT NOT NULL
);

CREATE INDEX job_events_user_date_idx ON job_events (user_id, event_date);

CREATE TABLE financial_records (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    month VARCHAR(7) NOT NULL,
    spend BIGINT NOT NULL CHECK (spend >= 0),
    bill BIGINT NOT NULL CHECK (bill >= 0),
    balance BIGINT NOT NULL CHECK (balance >= 0),
    credit_score INTEGER,
    income BIGINT NOT NULL CHECK (income >= 0),
    CONSTRAINT financial_records_user_month_unique UNIQUE (user_id, month)
);
