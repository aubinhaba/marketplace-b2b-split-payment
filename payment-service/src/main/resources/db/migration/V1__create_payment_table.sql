CREATE TABLE payments
(
    id          UUID           NOT NULL PRIMARY KEY,
    customer_id VARCHAR(255)   NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    currency    CHAR(3)        NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);
