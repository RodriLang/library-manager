package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiendanubeWebhookSignatureVerifierTest {

    @Test
    void validatesSignatureAgainstRawPayload() {
        TiendanubeProperties properties = new TiendanubeProperties(
                null,
                "secret",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        TiendanubeWebhookSignatureVerifier verifier = new TiendanubeWebhookSignatureVerifier(properties);
        String payload = "{\"store_id\":123,\"event\":\"order/paid\",\"id\":456}";
        String signature = verifier.calculate(payload, "secret");

        assertTrue(verifier.isValid(payload, signature));
        assertFalse(verifier.isValid(payload + " ", signature));
        assertFalse(verifier.isValid(payload, null));
        assertFalse(verifier.isValid(payload, "invalid"));
    }
}
