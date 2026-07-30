package com.rodrilang.librarymanager.integrations.tiendanube.exception;

import com.rodrilang.librarymanager.exception.BusinessException;

public class TiendanubeProductAlreadyExistsException extends BusinessException {

    public TiendanubeProductAlreadyExistsException(String message) {
        super(message);
    }
}