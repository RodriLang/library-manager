package com.rodrilang.librarymanager.fiscal.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecipientVatCondition {
    IVA_RESPONSABLE_INSCRIPTO(1),
    IVA_EXENTO(4),
    CONSUMIDOR_FINAL(5),
    MONOTRIBUTO(6);

    private final int arcaId;
}
