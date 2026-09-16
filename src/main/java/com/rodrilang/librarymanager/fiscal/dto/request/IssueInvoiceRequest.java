package com.rodrilang.librarymanager.fiscal.dto.request;

import com.rodrilang.librarymanager.fiscal.model.RecipientDocumentType;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IssueInvoiceRequest(

        @NotNull
        RecipientVatCondition recipientVatCondition,

        @NotNull
        RecipientDocumentType documentType,

        @Pattern(regexp = "\\d*", message = "El documento sólo puede contener números.")
        @Size(max = 20)
        String documentNumber,

        @Size(max = 200)
        String name,

        @Size(max = 250)
        String address

) {
}
