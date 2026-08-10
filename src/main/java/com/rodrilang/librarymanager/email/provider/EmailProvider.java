package com.rodrilang.librarymanager.email.provider;

import com.rodrilang.librarymanager.email.model.EmailMessage;

public interface EmailProvider {

    void send(EmailMessage message);

    String getName();
}
