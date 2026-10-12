package com.rodrilang.librarymanager.shipping.dto;
import java.util.List;
public record ShippingConnectionTestResponse(boolean success, String message, List<String> validated) {}
