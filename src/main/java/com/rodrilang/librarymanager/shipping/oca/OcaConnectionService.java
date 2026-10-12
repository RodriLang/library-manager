package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.dto.ShippingConnectionTestResponse;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OcaConnectionService {
    private final BookstoreContext bookstoreContext;
    private final BookstoreShippingConfigurationService configurationService;
    private final BookstoreShippingSettingsRepository settingsRepository;
    private final OcaSettingsReader settingsReader;
    private final OcaClientSupport support;

    public ShippingConnectionTestResponse test() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Map<String,String> credentials = configurationService.configuredCredentials(bookstoreId, ShippingProviderType.OCA);
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String cuit = support.requireSetting(settings, credentials, "cuit", "CUIT");
        int operativa = support.requireInt(credentials, settings, "operativa", "operativa");
        String cp = settingsRepository.findByBookstoreId(bookstoreId).map(s -> s.getOriginPostalCode()).filter(v -> v!=null&&!v.isBlank())
                .orElseThrow(() -> new BusinessException("Configurá el código postal de origen antes de probar OCA."));
        String xml = support.post(settings, "Tarifar_Envio_Corporativo", Map.of(
                "Cuit", cuit, "Operativa", operativa, "PesoTotal", "0.5", "VolumenTotal", "0.001",
                "CodigoPostalOrigen", cp.replaceAll("[^0-9]", ""), "CodigoPostalDestino", Objects.toString(settings.getOrDefault("testPostalCode", "1000")),
                "CantidadPaquetes", 1, "ValorDeclarado", 1000
        ));
        Document doc = OcaXmlSupport.parse(xml);
        if (OcaXmlSupport.elements(doc, "Table").isEmpty()) throw new BusinessException("OCA respondió, pero no devolvió una tarifa de prueba.");
        List<String> validated = List.of("CUIT", "OPERATIVA", "COTIZADOR");
        boolean epakConfigured = credentials.containsKey("usuario") && credentials.containsKey("password");
        String message = epakConfigured
                ? "Cotizador OCA validado. Las credenciales e-Pak están configuradas, pero este test no valida usuario ni contraseña."
                : "Cotizador OCA validado. Todavía faltan configurar las credenciales e-Pak para crear y anular envíos.";
        return new ShippingConnectionTestResponse(true, message, validated);
    }
}
