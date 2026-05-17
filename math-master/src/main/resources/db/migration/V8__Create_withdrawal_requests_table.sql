-- V8__Create_withdrawal_requests_table.sql
-- Creates the withdrawal_requests table for the manual withdrawal feature.
-- Idempotent: safe when Hibernate or a prior run already created the table.

CREATE TABLE IF NOT EXISTS withdrawal_requests (
    withdrawal_request_id UUID         NOT NULL DEFAULT gen_random_uuid(),
    wallet_id             UUID         NOT NULL,
    user_id               UUID         NOT NULL,
    amount                DECIMAL(15, 2) NOT NULL,
    bank_name             VARCHAR(100) NOT NULL,
    bank_account_number   VARCHAR(50)  NOT NULL,
    bank_account_name     VARCHAR(100) NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    otp_code              VARCHAR(72),
    otp_expiry            TIMESTAMP,
    proof_image_url       TEXT,
    admin_note            TEXT,
    transaction_id        UUID,

    -- Audit columns from BaseEntity
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMP,
    updated_by            UUID,
    deleted_at            TIMESTAMP,
    deleted_by            UUID,

    CONSTRAINT pk_withdrawal_requests PRIMARY KEY (withdrawal_request_id),
    CONSTRAINT fk_wr_wallet      FOREIGN KEY (wallet_id)      REFERENCES wallets(wallet_id)               ON DELETE RESTRICT,
    CONSTRAINT fk_wr_user        FOREIGN KEY (user_id)        REFERENCES users(id)                        ON DELETE RESTRICT,
    CONSTRAINT fk_wr_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)     ON DELETE SET NULL
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_wr_user_id        ON withdrawal_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_wr_wallet_id      ON withdrawal_requests(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wr_status         ON withdrawal_requests(status);
CREATE INDEX IF NOT EXISTS idx_wr_otp_expiry     ON withdrawal_requests(otp_expiry) WHERE status = 'PENDING_VERIFY';
CREATE INDEX IF NOT EXISTS idx_wr_user_status    ON withdrawal_requests(user_id, status);
