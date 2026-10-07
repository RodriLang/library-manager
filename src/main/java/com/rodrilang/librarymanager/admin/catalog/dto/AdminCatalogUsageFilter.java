package com.rodrilang.librarymanager.admin.catalog.dto;

/**
 * Filters catalog books by whether they are currently used by at least one bookstore.
 * An inventory row counts as usage only while it is active; stock may be zero.
 */
public enum AdminCatalogUsageFilter {
    IN_INVENTORY,
    NOT_IN_INVENTORY
}
