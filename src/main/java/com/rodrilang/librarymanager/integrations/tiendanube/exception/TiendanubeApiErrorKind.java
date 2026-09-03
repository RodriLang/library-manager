package com.rodrilang.librarymanager.integrations.tiendanube.exception;

public enum TiendanubeApiErrorKind {
    AUTHENTICATION,
    AUTHORIZATION,
    ACCESS_SUSPENDED,
    RATE_LIMIT,
    NOT_FOUND,
    CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT,
    NETWORK,
    UNKNOWN
}
