package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Min;

public record AddBookToInventoryRequest(

        @Min(0)
        Integer initialStock,

        @Min(0)
        Integer minimumStock

) {
}
