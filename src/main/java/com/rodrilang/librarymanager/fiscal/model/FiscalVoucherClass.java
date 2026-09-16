package com.rodrilang.librarymanager.fiscal.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FiscalVoucherClass {
    A(1),
    B(6),
    C(11);

    private final int arcaCode;
}
