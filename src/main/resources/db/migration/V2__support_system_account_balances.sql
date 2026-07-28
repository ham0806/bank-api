ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER';

UPDATE accounts
SET account_type = 'SYSTEM'
WHERE account_number = 'SYSTEM-CLEARING';

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_balance_minor_check;

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_balance_sign_check;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_balance_sign_check
        CHECK (account_type = 'SYSTEM' OR balance_minor >= 0);
