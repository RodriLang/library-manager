package com.rodrilang.librarymanager.editorialprice.service;


import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceRefreshResult;
import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface EffectiveEditorialPriceService {

    EffectiveEditorialPriceRefreshResult refreshForBooks(Collection<Long> bookIds, LocalDate affectedValidFrom);

    Optional<EffectiveEditorialPrice> findCurrentByBookId(Long bookId);

    Map<Long, EffectiveEditorialPrice> findCurrentByBookIds(Collection<Long> bookIds);
}