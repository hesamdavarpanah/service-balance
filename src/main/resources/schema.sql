CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    firstname VARCHAR(255),
    lastname VARCHAR(255),
    username VARCHAR(255),
    email VARCHAR(255),
    phone_number VARCHAR(50),
    password_hash VARCHAR(255),
    balance BIGINT DEFAULT 0,
    status VARCHAR(50),
    role VARCHAR(50)
    );


CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    transaction_method VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    account_id VARCHAR(255) NOT NULL,
    transaction_status VARCHAR(50) NOT NULL,

    CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id)
    REFERENCES accounts(id)
    );

CREATE INDEX IF NOT EXISTS idx_transactions_account_id
    ON transactions(account_id);