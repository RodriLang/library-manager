ALTER TABLE sales
    DROP CONSTRAINT IF EXISTS chk_sales_origin;

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_origin
        CHECK (origin IN ('MANUAL', 'TIENDANUBE', 'ANAQUEL_STORE'));
