package com.rodrilang.librarymanager.shipping.correoargentino;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.dto.ShippingConnectionTestResponse;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CorreoArgentinoConnectionService {
    private final BookstoreContext bookstoreContext;
    private final BookstoreShippingConfigurationService configurationService;
    private final CorreoArgentinoSettingsReader settingsReader;
    private final CorreoArgentinoClientSupport support;

    public ShippingConnectionTestResponse test() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Map<String,String> credentials = configurationService.configuredCredentials(bookstoreId, ShippingProviderType.CORREO_ARGENTINO);
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        List<String> validated = new ArrayList<>();
        if (support.hasMiCorreoCredentials(credentials)) {
            support.miCorreoToken(credentials, settings);
            validated.add("MICORREO");
        }
        if (support.hasPaqArCredentials(credentials)) {
            support.client(support.paqArBaseUrl(settings)).get().uri("/auth")
                    .headers(h -> support.addPaqArHeaders(h, credentials)).retrieve().toBodilessEntity();
            validated.add("PAQAR_V2");
        }
        if (validated.isEmpty()) {
            throw new BusinessException("Configurá credenciales de MiCorreo (usuario, password, customerId) y/o PAQ.AR 2.0 (agreement, apiKey).");
        }
        return new ShippingConnectionTestResponse(true, "Conexión con Correo Argentino validada.", List.copyOf(validated));
    }
}
