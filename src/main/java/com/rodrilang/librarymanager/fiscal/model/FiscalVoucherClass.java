package com.rodrilang.librarymanager.fiscal.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FiscalVoucherClass {
    A(1, 3),
    B(6, 8),
    C(11, 13);

    private final int arcaCode;
    private final int creditNoteArcaCode;
}
