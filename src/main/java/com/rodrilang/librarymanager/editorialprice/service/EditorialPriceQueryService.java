package com.rodrilang.librarymanager.editorialprice.service;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceBookDetailResponse;

public interface EditorialPriceQueryService {

    EditorialPriceBookDetailResponse getBookDetail(Long bookId);
}