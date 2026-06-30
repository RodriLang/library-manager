package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateInventoryStatusRequest(

        @NotNull
        Boolean active

) {
}