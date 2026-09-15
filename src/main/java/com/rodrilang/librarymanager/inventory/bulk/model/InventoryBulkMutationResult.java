package com.rodrilang.librarymanager.inventory.bulk.model;

public record InventoryBulkMutationResult(

        boolean changed,

        boolean syncStock,

        boolean syncPrice

) {

    public static InventoryBulkMutationResult skipped() {
        return new InventoryBulkMutationResult(
                false,
                false,
                false
        );
    }

    public static InventoryBulkMutationResult changed() {
        return new InventoryBulkMutationResult(
                true,
                false,
                false
        );
    }

    public static InventoryBulkMutationResult changed(
            boolean syncStock,
            boolean syncPrice
    ) {
        return new InventoryBulkMutationResult(
                true,
                syncStock,
                syncPrice
        );
    }
}