ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_reserved_not_over_balance;

ALTER TABLE accounts
    ADD CONSTRAINT accounts_reserved_not_over_balance
        CHECK (account_type = 'SYSTEM' OR reserved_minor <= balance_minor);
