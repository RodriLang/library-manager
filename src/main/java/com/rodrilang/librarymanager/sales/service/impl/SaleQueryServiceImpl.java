package com.rodrilang.librarymanager.sales.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.sales.dto.SaleFilter;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleResponse;
import com.rodrilang.librarymanager.sales.mapper.SaleMapper;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.repository.SaleItemRepository;
import com.rodrilang.librarymanager.sales.repository.SalePaymentRepository;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import com.rodrilang.librarymanager.sales.repository.SaleSpecifications;
import com.rodrilang.librarymanager.sales.repository.projection.SaleItemsSummaryProjection;
import com.rodrilang.librarymanager.sales.service.SaleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleQueryServiceImpl implements SaleQueryService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final SaleMapper mapper;
    private final BookstoreContext bookstoreContext;

    @Override
    @Transactional(readOnly = true)
    public Page<SaleResponse> findAll(SaleFilter filter, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        SaleFilter effectiveFilter = filter != null
                ? filter
                : new SaleFilter(null, null, null, null);

        if (effectiveFilter.from() != null
                && effectiveFilter.to() != null
                && effectiveFilter.from().isAfter(effectiveFilter.to())) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        Specification<Sale> specification = Specification.allOf(
                SaleSpecifications.bookstoreId(bookstoreId),
                SaleSpecifications.status(effectiveFilter.status()),
                SaleSpecifications.origin(effectiveFilter.origin()),
                SaleSpecifications.soldAtFrom(effectiveFilter.from()),
                SaleSpecifications.soldAtTo(effectiveFilter.to())
        );

        Page<Sale> page = saleRepository.findAll(specification, pageable);
        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, SaleItemsSummaryProjection> summaries = itemRepository
                .findSummariesBySaleIds(
                        page.getContent().stream().map(Sale::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        SaleItemsSummaryProjection::getSaleId,
                        Function.identity()
                ));

        return page.map(sale -> {
            SaleItemsSummaryProjection summary = summaries.get(sale.getId());

            return mapper.toResponse(
                    sale,
                    summary != null ? Math.toIntExact(summary.getItemCount()) : 0,
                    summary != null ? Math.toIntExact(summary.getTotalUnits()) : 0
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public SaleDetailResponse findById(Long saleId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Sale sale = saleRepository.findByIdAndBookstoreId(saleId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la venta con ID: " + saleId
                ));

        return mapper.toDetailResponse(
                sale,
                itemRepository.findAllBySaleIdOrderByIdAsc(sale.getId()),
                paymentRepository.findAllBySaleIdOrderByIdAsc(sale.getId())
        );
    }
}
