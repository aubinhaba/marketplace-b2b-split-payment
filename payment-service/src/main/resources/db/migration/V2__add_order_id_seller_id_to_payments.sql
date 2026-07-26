-- order_id correlates the payment with the order that triggered it; seller_id is the Stripe Connected Account
-- receiving the split. No foreign key to orders: it lives in another service's database.

-- The DEFAULT only exists to backfill rows written before this migration, then it is dropped so new rows must supply both.
ALTER TABLE payments
    ADD COLUMN order_id  VARCHAR(36)  NOT NULL DEFAULT 'migration-placeholder',
    ADD COLUMN seller_id VARCHAR(255) NOT NULL DEFAULT 'migration-placeholder';

ALTER TABLE payments
    ALTER COLUMN order_id  DROP DEFAULT,
    ALTER COLUMN seller_id DROP DEFAULT;
