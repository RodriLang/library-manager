package com.rodrilang.librarymanager.fiscal.client;

import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAccessTicket;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.exception.ArcaApiException;
import com.rodrilang.librarymanager.fiscal.exception.ArcaCommunicationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ArcaWsaaClient {

    @Qualifier("arcaRestClient")
    private final RestClient restClient;
    private final ArcaProperties properties;
    private final ArcaCmsSigner cmsSigner;

    private final AtomicReference<ArcaAccessTicket> cache = new AtomicReference<>();

    public ArcaWsaaClient(
            @Qualifier("arcaRestClient") RestClient restClient,
            ArcaProperties properties,
            ArcaCmsSigner cmsSigner
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.cmsSigner = cmsSigner;
    }

    public ArcaAccessTicket accessTicket() {
        Instant now = Instant.now();
        ArcaAccessTicket current = cache.get();
        if (current != null && current.isValidAt(now)) return current;

        synchronized (cache) {
            current = cache.get();
            if (current != null && current.isValidAt(now)) return current;

            ArcaAccessTicket fresh = requestAccessTicket(now);
            cache.set(fresh);
            return fresh;
        }
    }

    private ArcaAccessTicket requestAccessTicket(Instant now) {
        String tra = """
                <?xml version="1.0" encoding="UTF-8"?>
                <loginTicketRequest version="1.0">
                    <header>
                        <uniqueId>%d</uniqueId>
                        <generationTime>%s</generationTime>
                        <expirationTime>%s</expirationTime>
                    </header>
                    <service>%s</service>
                </loginTicketRequest>
                """.formatted(
                now.getEpochSecond(),
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(10, ChronoUnit.HOURS),
                XmlSupport.escape(properties.service())
        );

        String cms = cmsSigner.sign(tra);
        String envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:wsaa="http://wsaa.view.sua.dvadac.desein.afip.gov">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <wsaa:loginCms>
                            <wsaa:in0>%s</wsaa:in0>
                        </wsaa:loginCms>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(XmlSupport.escape(cms));

        try {
            String response = restClient.post()
                    .uri(properties.wsaaUrl())
                    .contentType(MediaType.TEXT_XML)
                    .header("SOAPAction", "urn:LoginCms")
                    .body(envelope)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new ArcaApiException("WSAA respondió sin contenido.");
            }

            var outer = XmlSupport.parse(response);
            String loginTicketXml = XmlSupport.firstText(outer, "loginCmsReturn")
                    .orElseThrow(() -> new ArcaApiException("WSAA no devolvió un ticket de acceso."));
            var ticket = XmlSupport.parse(loginTicketXml);

            String token = XmlSupport.firstText(ticket, "token")
                    .orElseThrow(() -> new ArcaApiException("WSAA no devolvió token."));
            String sign = XmlSupport.firstText(ticket, "sign")
                    .orElseThrow(() -> new ArcaApiException("WSAA no devolvió firma."));
            Instant expiration = XmlSupport.firstText(ticket, "expirationTime")
                    .map(Instant::parse)
                    .orElse(now.plus(9, ChronoUnit.HOURS));

            return new ArcaAccessTicket(token, sign, expiration);
        } catch (RestClientException exception) {
            throw new ArcaCommunicationException("No se pudo comunicar con WSAA de ARCA.", exception);
        }
    }
}
