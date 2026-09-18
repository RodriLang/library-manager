package com.rodrilang.librarymanager.fiscal.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecipientDocumentType {
    CUIT(80),
    DNI(96),
    CONSUMIDOR_FINAL(99);

    private final int arcaCode;
}
