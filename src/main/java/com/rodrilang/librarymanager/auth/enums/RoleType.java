package com.rodrilang.librarymanager.auth.enums;

/**
 * Roles base del modelo de acceso.
 *
 * ADMIN: administrador global de la plataforma. No requiere librería asociada.
 * BOOKSTORE_ADMIN: administrador de una membresía de librería.
 * BOOKSTORE_USER: usuario estándar de una membresía de librería.
 *
 * Las capacidades concretas de cada rol se resuelven mediante permissions/authorities.
 */
public enum RoleType {

    ADMIN,
    BOOKSTORE_ADMIN,
    BOOKSTORE_USER
}
