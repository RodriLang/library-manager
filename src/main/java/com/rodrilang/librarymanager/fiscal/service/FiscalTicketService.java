package com.rodrilang.librarymanager.fiscal.service;

public interface FiscalTicketService {
    String generate(Long documentId, boolean autoPrint);
}
