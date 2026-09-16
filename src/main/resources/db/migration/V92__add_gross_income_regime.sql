ALTER TABLE bookstore_fiscal_settings
    ADD COLUMN gross_income_regime VARCHAR(40);

ALTER TABLE bookstore_fiscal_settings
    ALTER COLUMN gross_income_number DROP NOT NULL;

ALTER TABLE bookstore_fiscal_settings
    ADD CONSTRAINT chk_bookstore_fiscal_settings_gross_income_regime
        CHECK (
            gross_income_regime IS NULL
                OR gross_income_regime IN (
                                           'LOCAL',
                                           'MULTILATERAL_AGREEMENT',
                                           'EXEMPT',
                                           'NOT_REGISTERED'
                )
            );
