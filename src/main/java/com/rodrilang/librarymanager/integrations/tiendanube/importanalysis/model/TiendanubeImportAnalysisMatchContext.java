package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.model.Inventory;

import java.util.List;
import java.util.Map;

public record TiendanubeImportAnalysisMatchContext(
        List<Inventory> inventories,
        Map<String, List<Inventory>> inventoriesByIsbn,
        Map<Long, TiendanubeProductLink> linksByVariantId,
        Map<Long, TiendanubeProductLink> linksByInventoryId
) {
}
