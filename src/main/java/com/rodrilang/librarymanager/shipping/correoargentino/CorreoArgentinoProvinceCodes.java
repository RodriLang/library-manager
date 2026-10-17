package com.rodrilang.librarymanager.shipping.correoargentino;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class CorreoArgentinoProvinceCodes {
    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("SALTA","A"), Map.entry("BUENOS AIRES","B"), Map.entry("PROVINCIA DE BUENOS AIRES","B"),
            Map.entry("CABA","C"), Map.entry("CIUDAD AUTONOMA DE BUENOS AIRES","C"), Map.entry("CAPITAL FEDERAL","C"),
            Map.entry("SAN LUIS","D"), Map.entry("ENTRE RIOS","E"), Map.entry("LA RIOJA","F"), Map.entry("SANTIAGO DEL ESTERO","G"),
            Map.entry("CHACO","H"), Map.entry("SAN JUAN","J"), Map.entry("CATAMARCA","K"), Map.entry("LA PAMPA","L"),
            Map.entry("MENDOZA","M"), Map.entry("MISIONES","N"), Map.entry("FORMOSA","P"), Map.entry("NEUQUEN","Q"),
            Map.entry("RIO NEGRO","R"), Map.entry("SANTA FE","S"), Map.entry("TUCUMAN","T"), Map.entry("CHUBUT","U"),
            Map.entry("TIERRA DEL FUEGO","V"), Map.entry("CORRIENTES","W"), Map.entry("CORDOBA","X"), Map.entry("JUJUY","Y"),
            Map.entry("SANTA CRUZ","Z")
    );
    private CorreoArgentinoProvinceCodes() {}
    public static String code(String value) {
        if (value == null || value.isBlank()) return null;
        String v = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
        if (v.length() == 1 && "ABCDEFGHJKLMNOPQRSTUVWXYZ".contains(v)) return v;
        return CODES.get(v);
    }
}
