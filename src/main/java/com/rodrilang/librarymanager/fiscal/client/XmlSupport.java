package com.rodrilang.librarymanager.fiscal.client;

import com.rodrilang.librarymanager.fiscal.exception.ArcaApiException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class XmlSupport {

    private XmlSupport() {
    }

    static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new ArcaApiException("ARCA devolvió una respuesta XML inválida.", exception);
        }
    }

    static Optional<String> firstText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) return Optional.empty();
        return Optional.ofNullable(nodes.item(0).getTextContent()).map(String::trim);
    }

    static List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> elements = new ArrayList<>(nodes.getLength());

        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) elements.add(element);
        }

        return elements;
    }

    static Optional<String> childText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) return Optional.empty();
        return Optional.ofNullable(nodes.item(0).getTextContent()).map(String::trim);
    }

    static String escape(String value) {
        if (value == null) return "";

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
