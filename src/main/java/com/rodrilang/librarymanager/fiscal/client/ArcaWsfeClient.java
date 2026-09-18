package com.rodrilang.librarymanager.fiscal.client;

import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAccessTicket;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAuthorizationResult;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaPointOfSale;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaVoucherInfo;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.exception.ArcaApiException;
import com.rodrilang.librarymanager.fiscal.exception.ArcaCommunicationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ArcaWsfeClient {

    private static final DateTimeFormatter ARCA_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String NAMESPACE = "http://ar.gov.afip.dif.FEV1/";

    @Qualifier("arcaRestClient")
    private final RestClient restClient;
    private final ArcaProperties properties;
    private final ArcaWsaaClient wsaaClient;

    public ArcaWsfeClient(
            @Qualifier("arcaRestClient") RestClient restClient,
            ArcaProperties properties,
            ArcaWsaaClient wsaaClient
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.wsaaClient = wsaaClient;
    }

    public List<ArcaPointOfSale> getPointsOfSale(long representedCuit) {
        Document document = execute(
                "FEParamGetPtosVenta",
                authXml(representedCuit)
        );
        assertNoGlobalErrors(document);

        List<ArcaPointOfSale> result = new ArrayList<>();
        for (Element element : XmlSupport.elements(document, "PtoVenta")) {
            int number = XmlSupport.childText(element, "Nro").map(Integer::parseInt).orElse(0);
            String emissionType = XmlSupport.childText(element, "EmisionTipo").orElse(null);
            boolean active = XmlSupport.childText(element, "FchBaja")
                    .map(String::isBlank)
                    .orElse(true);

            result.add(new ArcaPointOfSale(number, emissionType, active));
        }

        return result;
    }

    public void verifyAccess(long representedCuit) {
        Document document = execute(
                "FEParamGetTiposCbte",
                authXml(representedCuit)
        );

        assertNoGlobalErrors(document);

        if (XmlSupport.elements(document, "CbteTipo").isEmpty()) {
            throw new ArcaApiException(
                    "ARCA respondió correctamente, pero no devolvió tipos de comprobante habilitados."
            );
        }
    }

    public long getLastAuthorized(long representedCuit, int pointOfSale, int voucherType) {
        String body = authXml(representedCuit) + """
                <ar:PtoVta>%d</ar:PtoVta>
                <ar:CbteTipo>%d</ar:CbteTipo>
                """.formatted(pointOfSale, voucherType);

        Document document = execute("FECompUltimoAutorizado", body);
        assertNoGlobalErrors(document);

        return XmlSupport.firstText(document, "CbteNro")
                .map(Long::parseLong)
                .orElse(0L);
    }

    public ArcaAuthorizationResult authorize(ArcaInvoiceRequest request) {
        String associatedVoucherXml = associatedVoucherXml(request);

        String body = authXml(request.representedCuit()) + """
                <ar:FeCAEReq>
                    <ar:FeCabReq>
                        <ar:CantReg>1</ar:CantReg>
                        <ar:PtoVta>%d</ar:PtoVta>
                        <ar:CbteTipo>%d</ar:CbteTipo>
                    </ar:FeCabReq>
                    <ar:FeDetReq>
                        <ar:FECAEDetRequest>
                            <ar:Concepto>1</ar:Concepto>
                            <ar:DocTipo>%d</ar:DocTipo>
                            <ar:DocNro>%d</ar:DocNro>
                            <ar:CbteDesde>%d</ar:CbteDesde>
                            <ar:CbteHasta>%d</ar:CbteHasta>
                            <ar:CbteFch>%s</ar:CbteFch>
                            <ar:ImpTotal>%s</ar:ImpTotal>
                            <ar:ImpTotConc>0.00</ar:ImpTotConc>
                            <ar:ImpNeto>%s</ar:ImpNeto>
                            <ar:ImpOpEx>%s</ar:ImpOpEx>
                            <ar:ImpTrib>0.00</ar:ImpTrib>
                            <ar:ImpIVA>0.00</ar:ImpIVA>
                            <ar:MonId>PES</ar:MonId>
                            <ar:MonCotiz>1.000000</ar:MonCotiz>
                            <ar:CondicionIVAReceptorId>%d</ar:CondicionIVAReceptorId>
                            %s
                        </ar:FECAEDetRequest>
                    </ar:FeDetReq>
                </ar:FeCAEReq>
                """.formatted(
                request.pointOfSale(),
                request.voucherType(),
                request.recipientDocumentType(),
                request.recipientDocumentNumber(),
                request.voucherNumber(),
                request.voucherNumber(),
                ARCA_DATE.format(request.issueDate()),
                money(request.total()),
                money(request.netAmount()),
                money(request.exemptAmount()),
                request.recipientVatConditionId(),
                associatedVoucherXml
        );

        Document document = execute("FECAESolicitar", body);
        String errors = collectMessages(document, "Err");
        String observations = collectMessages(document, "Obs");
        String result = XmlSupport.firstText(document, "Resultado").orElse("R");
        String cae = XmlSupport.firstText(document, "CAE").filter(value -> !value.isBlank()).orElse(null);
        LocalDate expiration = XmlSupport.firstText(document, "CAEFchVto")
                .filter(value -> value.length() == 8)
                .map(value -> LocalDate.parse(value, ARCA_DATE))
                .orElse(null);

        return new ArcaAuthorizationResult(
                "A".equalsIgnoreCase(result) && cae != null,
                result,
                cae,
                expiration,
                observations,
                errors
        );
    }

    private String associatedVoucherXml(ArcaInvoiceRequest request) {
        if (!request.hasAssociatedVoucher()) return "";

        String issueDate = request.associatedIssueDate() != null
                ? "<ar:CbteFch>" + ARCA_DATE.format(request.associatedIssueDate()) + "</ar:CbteFch>"
                : "";

        return """
                <ar:CbtesAsoc>
                    <ar:CbteAsoc>
                        <ar:Tipo>%d</ar:Tipo>
                        <ar:PtoVta>%d</ar:PtoVta>
                        <ar:Nro>%d</ar:Nro>
                        <ar:Cuit>%d</ar:Cuit>
                        %s
                    </ar:CbteAsoc>
                </ar:CbtesAsoc>
                """.formatted(
                request.associatedVoucherType(),
                request.associatedPointOfSale(),
                request.associatedVoucherNumber(),
                request.representedCuit(),
                issueDate
        );
    }

    public Optional<ArcaVoucherInfo> consult(
            long representedCuit,
            int pointOfSale,
            int voucherType,
            long voucherNumber
    ) {
        String body = authXml(representedCuit) + """
                <ar:FeCompConsReq>
                    <ar:CbteTipo>%d</ar:CbteTipo>
                    <ar:CbteNro>%d</ar:CbteNro>
                    <ar:PtoVta>%d</ar:PtoVta>
                </ar:FeCompConsReq>
                """.formatted(voucherType, voucherNumber, pointOfSale);

        Document document = execute("FECompConsultar", body);
        String errors = collectMessages(document, "Err");
        if (!errors.isBlank() && XmlSupport.elements(document, "ResultGet").isEmpty()) {
            return Optional.empty();
        }

        Optional<Element> resultGet = XmlSupport.elements(document, "ResultGet").stream().findFirst();
        if (resultGet.isEmpty()) return Optional.empty();

        Element result = resultGet.get();
        String cae = XmlSupport.childText(result, "CodAutorizacion").orElse(null);
        if (cae == null || cae.isBlank()) return Optional.empty();

        LocalDate issueDate = XmlSupport.childText(result, "CbteFch")
                .filter(value -> value.length() == 8)
                .map(value -> LocalDate.parse(value, ARCA_DATE))
                .orElse(null);
        BigDecimal total = XmlSupport.childText(result, "ImpTotal")
                .map(BigDecimal::new)
                .orElse(BigDecimal.ZERO);
        LocalDate expiration = XmlSupport.childText(result, "FchVto")
                .filter(value -> value.length() == 8)
                .map(value -> LocalDate.parse(value, ARCA_DATE))
                .orElse(null);
        String resultCode = XmlSupport.childText(result, "Resultado").orElse("A");
        String observations = collectMessages(result, "Obs");

        return Optional.of(new ArcaVoucherInfo(
                voucherNumber,
                issueDate,
                total,
                cae,
                expiration,
                resultCode,
                observations
        ));
    }

    private Document execute(String operation, String body) {
        String envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ar="%s">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <ar:%s>
                            %s
                        </ar:%s>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(NAMESPACE, operation, body, operation);

        try {
            String response = restClient.post()
                    .uri(properties.wsfeUrl())
                    .contentType(MediaType.TEXT_XML)
                    .header("SOAPAction", "\"" + NAMESPACE + operation + "\"")
                    .body(envelope)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new ArcaApiException("ARCA respondió sin contenido para " + operation + ".");
            }

            return XmlSupport.parse(response);
        } catch (RestClientException exception) {
            throw new ArcaCommunicationException(
                    "No se pudo completar la comunicación con ARCA durante " + operation + ".",
                    exception
            );
        }
    }

    private String authXml(long representedCuit) {
        ArcaAccessTicket ticket = wsaaClient.accessTicket();

        return """
                <ar:Auth>
                    <ar:Token>%s</ar:Token>
                    <ar:Sign>%s</ar:Sign>
                    <ar:Cuit>%d</ar:Cuit>
                </ar:Auth>
                """.formatted(
                XmlSupport.escape(ticket.token()),
                XmlSupport.escape(ticket.sign()),
                representedCuit
        );
    }

    private void assertNoGlobalErrors(Document document) {
        String errors = collectMessages(document, "Err");
        if (!errors.isBlank()) throw new ArcaApiException("ARCA rechazó la consulta: " + errors);
    }

    private String collectMessages(Document document, String elementName) {
        return collectMessages(XmlSupport.elements(document, elementName), elementName);
    }

    private String collectMessages(Element parent, String elementName) {
        var nodes = parent.getElementsByTagNameNS("*", elementName);
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) elements.add(element);
        }
        return collectMessages(elements, elementName);
    }

    private String collectMessages(List<Element> elements, String elementName) {
        List<String> messages = new ArrayList<>();
        for (Element element : elements) {
            String code = XmlSupport.childText(element, "Code").orElse("");
            String message = XmlSupport.childText(element, "Msg").orElse("");
            if (!message.isBlank()) {
                messages.add(code.isBlank() ? message : code + ": " + message);
            }
        }
        return String.join(" | ", messages);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
