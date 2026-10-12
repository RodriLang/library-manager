package com.rodrilang.librarymanager.shipping.correoargentino;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.operations.*;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class CorreoArgentinoPickupPointClient implements ShippingPickupPointClient {
    private final CorreoArgentinoClientSupport support;
    private final CorreoArgentinoSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;
    @Override public ShippingProviderType provider() { return ShippingProviderType.CORREO_ARGENTINO; }
    @Override public List<ShippingPickupPoint> findByPostalCode(Long bookstoreId, String postalCode) {
        // PAQ.AR permite consultar todas las agencias y filtrar por disponibilidad. Si el CPA incluye letra inicial,
        // se utiliza como código ISO de provincia; para CP numérico se consulta sin stateId y se filtra por CP.
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        JsonNode response = support.client(support.paqArBaseUrl(settings)).get().uri(uri -> {
            var b=uri.path("/agencies").queryParam("pickup_availability", true);
            String normalized=postalCode==null?"":postalCode.trim().toUpperCase(Locale.ROOT);
            if(!normalized.isBlank() && Character.isLetter(normalized.charAt(0))) b.queryParam("stateId", String.valueOf(normalized.charAt(0)));
            return b.build();
        }).headers(h -> support.addPaqArHeaders(h, credentials)).retrieve().body(JsonNode.class);
        if(response==null || !response.isArray()) return List.of();
        List<ShippingPickupPoint> out=new ArrayList<>();
        String target=postalCode==null?"":postalCode.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        for(JsonNode a:response){
            JsonNode loc=a.path("location"); String cp=loc.path("zip_code").asText("");
            if(!target.isBlank() && !cp.isBlank() && !samePostal(target,cp)) continue;
            JsonNode geo=loc.path("geolocation");
            out.add(new ShippingPickupPoint(a.path("agency_id").asText(),a.path("agency_name").asText(),
                    (loc.path("street_name").asText("")+" "+loc.path("street_number").asText("")).trim(),cp,
                    loc.path("city_name").asText(null),loc.path("state_name").asText(null),dbl(geo.path("latitude")),dbl(geo.path("longitude"))));
        }
        return out;
    }
    private boolean samePostal(String a,String b){ String aa=a.replaceAll("[^0-9]",""); String bb=b.replaceAll("[^0-9]",""); return !aa.isBlank() && aa.equals(bb); }
    private Double dbl(JsonNode n){ try{return n.isMissingNode()?null:Double.valueOf(n.asText());}catch(Exception e){return null;} }
}
