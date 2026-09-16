package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.IssuerTaxCondition;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;
import org.springframework.stereotype.Component;

@Component
public class FiscalVoucherResolver {

    public FiscalVoucherClass resolve(
            IssuerTaxCondition issuer,
            RecipientVatCondition recipient
    ) {
        if (issuer != IssuerTaxCondition.IVA_RESPONSABLE_INSCRIPTO) {
            return FiscalVoucherClass.C;
        }

        if (
                recipient == RecipientVatCondition.IVA_RESPONSABLE_INSCRIPTO
                        || recipient == RecipientVatCondition.MONOTRIBUTO
        ) {
            return FiscalVoucherClass.A;
        }

        return FiscalVoucherClass.B;
    }
}
