package com.rodrilang.librarymanager.fiscal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueCreditNoteRequest(

        @NotBlank(message = "Debe indicarse el motivo de la nota de crédito.")
        @Size(max = 500)
        String reason

) {
}
