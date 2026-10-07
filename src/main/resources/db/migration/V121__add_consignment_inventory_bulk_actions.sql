-- V121__add_consignment_inventory_bulk_actions.sql

ALTER TABLE inventory_bulk_operations
    DROP CONSTRAINT IF EXISTS ck_inventory_bulk_operations_action;

ALTER TABLE inventory_bulk_operations
    ADD CONSTRAINT ck_inventory_bulk_operations_action
        CHECK (
            action IN (
                       'SET_MINIMUM_STOCK',
                       'MARK_FOR_REPLENISHMENT',
                       'ACTIVATE',
                       'DEACTIVATE',
                       'ENABLE_EDITORIAL_PRICE_SYNC',
                       'DISABLE_EDITORIAL_PRICE_SYNC',
                       'MARK_AS_CONSIGNMENT',
                       'CLEAR_CONSIGNMENT'
                )
            );