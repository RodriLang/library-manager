package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

final class OcaXmlSupport {
    private OcaXmlSupport() {}

    static Document parse(String xml) {
        if (xml == null || xml.isBlank()) throw new BusinessException("OCA devolvió una respuesta vacía.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception ex) {
            throw new BusinessException("OCA devolvió una respuesta XML que no pudimos interpretar.");
        }
    }

    static List<Element> elements(Document document, String tag) {
        NodeList list = document.getElementsByTagName(tag);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) if (list.item(i) instanceof Element e) result.add(e);
        return result;
    }

    static String text(Element parent, String... names) {
        for (String name : names) {
            NodeList list = parent.getElementsByTagName(name);
            if (list.getLength() > 0) {
                String value = list.item(0).getTextContent();
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return null;
    }

    static String first(Document document, String... names) {
        for (String name : names) {
            NodeList list = document.getElementsByTagName(name);
            if (list.getLength() > 0) {
                String value = list.item(0).getTextContent();
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return null;
    }

    static String attribute(Element element, String... names) {
        for (String name : names) {
            if (element.hasAttribute(name)) {
                String value = element.getAttribute(name);
                if (!value.isBlank()) return value.trim();
            }
        }
        return null;
    }

    static String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
