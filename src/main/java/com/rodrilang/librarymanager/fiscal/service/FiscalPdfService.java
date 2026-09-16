package com.rodrilang.librarymanager.fiscal.service;

public interface FiscalPdfService {

    byte[] generate(Long documentId);
}
