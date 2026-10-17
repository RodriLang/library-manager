package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPoint;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPointClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.*;

@Component
@RequiredArgsConstructor
public class OcaPickupPointClient implements ShippingPickupPointClient {
    private final OcaSettingsReader settingsReader;
    private final OcaClientSupport support;

    @Override public ShippingProviderType provider() { return ShippingProviderType.OCA; }

    @Override
    public List<ShippingPickupPoint> findByPostalCode(Long bookstoreId, String postalCode) {
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String xml = support.post(settings, "GetCentrosImposicionConServiciosByCP", Map.of("CodigoPostal", numericPostal(postalCode)));
        Document doc = OcaXmlSupport.parse(xml);
        List<ShippingPickupPoint> result = new ArrayList<>();
        for (Element table : OcaXmlSupport.elements(doc, "Table")) {
            String delivery = OcaXmlSupport.text(table, "Entrega", "EntregaPaquetes", "ServicioEntrega", "Entrega_de_Paquetes");
            if (delivery != null && !truthy(delivery)) continue;
            String id = OcaXmlSupport.text(table, "idCentroImposicion", "IdCentroImposicion", "IDCentroImposicion", "IdCentro");
            if (id == null) continue;
            String name = first(table, "Sigla", "Sucursal", "Descripcion", "Nombre");
            String street = first(table, "Calle", "Domicilio", "Direccion");
            String number = first(table, "Numero", "Nro", "Altura");
            String cp = first(table, "CodigoPostal", "CP", "Codigo_Postal");
            String city = first(table, "Localidad", "Ciudad");
            String state = first(table, "Provincia");
            String address = ((street == null ? "" : street) + (number == null ? "" : " " + number)).trim();
            result.add(new ShippingPickupPoint(id, name == null ? "Sucursal OCA" : name, address, cp, city, state, null, null));
        }
        return result;
    }

    private int numericPostal(String value) { String digits=value==null?"":value.replaceAll("[^0-9]",""); return digits.isBlank()?0:Integer.parseInt(digits); }
    private String first(Element element, String... tags) { return OcaXmlSupport.text(element, tags); }
    private boolean truthy(String value) { String v=value.trim().toUpperCase(Locale.ROOT); return v.equals("1")||v.equals("TRUE")||v.equals("SI")||v.equals("S")||v.contains("ENTREGA"); }
}
