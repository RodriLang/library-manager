package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.quote.provider.ShippingPackage;
import com.rodrilang.librarymanager.shipping.quote.provider.ShippingProviderQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OcaQuoteClient {
    private final OcaClientSupport support;

    public ShippingProviderQuote quoteHome(String originPostalCode, String destinationPostalCode, ShippingPackage pack,
                                           Map<String, String> credentials, Map<String, Object> settings) {
        String cuit = support.require(credentials, "cuit", "CUIT");
        int operativa = support.requireInt(credentials, settings, "operativa", "operativa");
        BigDecimal weightKg = BigDecimal.valueOf(pack.weightGrams()).divide(BigDecimal.valueOf(1000), 3, RoundingMode.CEILING);
        BigDecimal volumeM3 = pack.volumeCm3().divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.CEILING);
        String xml = support.post(settings, "Tarifar_Envio_Corporativo", Map.of(
                "Cuit", cuit,
                "Operativa", operativa,
                "PesoTotal", weightKg.toPlainString(),
                "VolumenTotal", volumeM3.toPlainString(),
                "CodigoPostalOrigen", numericPostal(originPostalCode),
                "CodigoPostalDestino", numericPostal(destinationPostalCode),
                "CantidadPaquetes", 1,
                "ValorDeclarado", pack.declaredValue().setScale(0, RoundingMode.HALF_UP).toPlainString()
        ));
        Document doc = OcaXmlSupport.parse(xml);
        List<Element> tables = OcaXmlSupport.elements(doc, "Table");
        if (tables.isEmpty()) throw new BusinessException("OCA no devolvió una tarifa para ese destino.");
        Element table = tables.get(0);
        BigDecimal amount = decimal(OcaXmlSupport.text(table, "Total", "Precio"));
        String plazo = OcaXmlSupport.text(table, "PlazoEntrega");
        Integer days = integer(plazo);
        String tarifador = OcaXmlSupport.text(table, "Tarifador");
        String ambito = OcaXmlSupport.text(table, "Ambito");
        String metadata = "{\"operativa\":" + operativa + ",\"tarifador\":\"" + json(tarifador) + "\",\"ambito\":\"" + json(ambito) + "\"}";
        return new ShippingProviderQuote(ShippingProviderType.OCA, "OCA-" + operativa, "OCA · Domicilio",
                ShippingDeliveryType.HOME, amount, days, days, days == null ? plazo : days + " días hábiles", null, metadata);
    }

    private int numericPostal(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) throw new BusinessException("El código postal no es válido para OCA.");
        return Integer.parseInt(digits);
    }
    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("OCA devolvió una tarifa sin importe.");
        try { return new BigDecimal(value.replace(',', '.')).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException ex) { throw new BusinessException("OCA devolvió un importe de tarifa inválido."); }
    }
    private Integer integer(String value) { try { return value == null ? null : Integer.valueOf(value.replaceAll("[^0-9]", "")); } catch (Exception ex) { return null; } }
    private String json(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
