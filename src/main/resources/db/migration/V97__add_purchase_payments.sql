CREATE TABLE purchase_payments
(
    id                  BIGSERIAL PRIMARY KEY,
    purchase_id         BIGINT         NOT NULL,
    paid_at             TIMESTAMPTZ    NOT NULL,
    method              VARCHAR(30)    NOT NULL,
    amount              NUMERIC(16, 2) NOT NULL,
    reference           VARCHAR(100),
    notes               VARCHAR(1000),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_purchase_payments_purchase
        FOREIGN KEY (purchase_id)
            REFERENCES purchases (id),

    CONSTRAINT chk_purchase_payments_method
        CHECK (
            method IN (
                'CASH',
                'DEBIT_CARD',
                'CREDIT_CARD',
                'TRANSFER',
                'DIGITAL_WALLET',
                'OTHER'
            )
        ),

    CONSTRAINT chk_purchase_payments_amount
        CHECK (amount > 0),

    CONSTRAINT chk_purchase_payments_cancellation
        CHECK (
            (cancelled_at IS NULL AND cancellation_reason IS NULL)
            OR
            (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
        )
);

CREATE INDEX idx_purchase_payments_purchase_paid_at
    ON purchase_payments (purchase_id, paid_at);

CREATE INDEX idx_purchase_payments_active
    ON purchase_payments (purchase_id)
    WHERE cancelled_at IS NULL;
