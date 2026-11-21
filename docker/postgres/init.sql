CREATE TABLE public.accounts (
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

CREATE TABLE public.transfer_transactions (
                                              id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                                              created_at TIMESTAMP NOT NULL,
                                              updated_at TIMESTAMP NOT NULL,
                                              source_account_id VARCHAR(255) NOT NULL,
                                              destination_account_id VARCHAR(255) NOT NULL,
                                              amount BIGINT NOT NULL CHECK (amount > 0),
                                              transaction_status VARCHAR(50) NOT NULL,

                                              CONSTRAINT transfer_transactions_source_account_id_fkey
                                                  FOREIGN KEY (source_account_id)
                                                      REFERENCES public.accounts(id),

                                              CONSTRAINT transfer_transactions_destination_account_id_fkey
                                                  FOREIGN KEY (destination_account_id)
                                                      REFERENCES public.accounts(id),

                                              CONSTRAINT transfer_transactions_different_accounts_check
                                                  CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX idx_transfer_transactions_source_account_id
    ON public.transfer_transactions(source_account_id);

CREATE INDEX idx_transfer_transactions_destination_account_id
    ON public.transfer_transactions(destination_account_id);

CREATE TABLE public.transactions (
                                     id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
                                     created_at TIMESTAMP NOT NULL,
                                     updated_at TIMESTAMP NOT NULL,
                                     transaction_method VARCHAR(50) NOT NULL,
                                     amount BIGINT NOT NULL CHECK (amount > 0),
                                     account_id VARCHAR(255) NOT NULL,
                                     transaction_status VARCHAR(50) NOT NULL,

                                     CONSTRAINT transactions_account_id_fkey
                                         FOREIGN KEY (account_id)
                                             REFERENCES public.accounts(id)
);

CREATE INDEX idx_transactions_account_id
    ON public.transactions(account_id);