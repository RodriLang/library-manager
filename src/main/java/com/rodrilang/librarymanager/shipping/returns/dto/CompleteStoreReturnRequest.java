package com.rodrilang.librarymanager.shipping.returns.dto;

import jakarta.validation.Valid;
import java.util.List;

public record CompleteStoreReturnRequest(
        List<@Valid ReturnRestockItemRequest> restockItems
) {}
