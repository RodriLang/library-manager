package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import java.util.List;
import java.util.Map;

public record TiendanubeCreateProductRequest(

        Map<String, String> name,

        Map<String, String> description,

        List<TiendanubeCreateVariantRequest> variants,

        List<TiendanubeCreateImageRequest> images,

        Boolean published

) {
}