package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.cost.dto.request.BulkEstimateInventoryCostRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.request.BulkSetInventoryDiscountRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.request.SetInventoryCostRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.response.BulkInventoryCostFailureResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.response.BulkInventoryCostUpdateResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostLayerResponse;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostLayer;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostLayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCostCommandService {

    private final InventoryCostLayerRepository layerRepository;
    private final InventoryCostResponseMapper responseMapper;
    private final InventoryCostCalculator calculator;
    private final BookstoreContext bookstoreContext;

    @Transactional
    public InventoryCostLayerResponse setCost(Long layerId, SetInventoryCostRequest request) {
        InventoryCostLayer layer = requireLayer(layerId);
        applyCost(layer, request);
        return responseMapper.toResponse(layer);
    }

    @Transactional
    public BulkInventoryCostUpdateResponse estimateByDiscount(BulkEstimateInventoryCostRequest request) {
        List<Long> layerIds = request.layerIds().stream().sorted().toList();
        List<BulkInventoryCostFailureResponse> failures = new ArrayList<>();
        int updated = 0;

        for (Long layerId : layerIds) {
            try {
                InventoryCostLayer layer = requireLayer(layerId);
                estimateByDiscount(layer, request.discountPercentage());
                updated++;
            } catch (BusinessException exception) {
                failures.add(new BulkInventoryCostFailureResponse(layerId, exception.getMessage()));
            }
        }

        return new BulkInventoryCostUpdateResponse(updated, failures.size(), List.copyOf(failures));
    }


    @Transactional
    public BulkInventoryCostUpdateResponse setDiscount(BulkSetInventoryDiscountRequest request) {
        List<Long> layerIds = request.layerIds().stream().sorted().toList();
        List<BulkInventoryCostFailureResponse> failures = new ArrayList<>();
        int updated = 0;

        for (Long layerId : layerIds) {
            try {
                InventoryCostLayer layer = requireLayer(layerId);
                layer.setDiscountPercentage(calculator.percentage(request.discountPercentage()));
                updated++;
            } catch (BusinessException exception) {
                failures.add(new BulkInventoryCostFailureResponse(layerId, exception.getMessage()));
            }
        }

        return new BulkInventoryCostUpdateResponse(updated, failures.size(), List.copyOf(failures));
    }

    private InventoryCostLayer requireLayer(Long layerId) {
        return layerRepository.findByIdAndBookstoreIdForUpdate(
                layerId,
                bookstoreContext.getCurrentBookstoreId()
        ).orElseThrow(() -> new BusinessException("No se encontró la capa de costo indicada"));
    }

    private void applyCost(InventoryCostLayer layer, SetInventoryCostRequest request) {
        if (request.costType() == null && request.unitCost() == null && request.discountPercentage() == null) {
            throw new BusinessException("Debe informar al menos un dato económico para actualizar");
        }

        BigDecimal discount = calculator.percentage(request.discountPercentage());
        if (discount != null) {
            layer.setDiscountPercentage(discount);
        }

        if (request.costType() == null) {
            if (request.unitCost() != null) {
                throw new BusinessException("Para informar un costo unitario debe indicar si es REAL o ESTIMATED");
            }
            return;
        }

        if (request.costType() == InventoryCostType.UNKNOWN) {
            throw new BusinessException("No se puede borrar un costo conocido desde esta operación");
        }

        BigDecimal requestedUnitCost = calculator.money(request.unitCost());
        if (layer.getCostType() == InventoryCostType.REAL) {
            if (request.costType() != InventoryCostType.REAL) {
                throw new BusinessException("Un costo real confirmado no puede degradarse a estimado");
            }
            if (requestedUnitCost != null && requestedUnitCost.compareTo(layer.getUnitCost()) != 0) {
                throw new BusinessException("Un costo real confirmado no puede modificarse desde esta operación");
            }
            return;
        }

        if (request.costType() == InventoryCostType.REAL) {
            if (requestedUnitCost == null) {
                throw new BusinessException("Un costo real requiere informar el costo unitario pagado");
            }

            layer.setCostType(InventoryCostType.REAL);
            layer.setUnitCost(requestedUnitCost);
            return;
        }

        BigDecimal estimatedCost = requestedUnitCost;
        if (estimatedCost == null) {
            if (layer.getUnitCost() != null) {
                estimatedCost = layer.getUnitCost();
            } else if (discount != null) {
                estimatedCost = calculator.estimateFromDiscount(layer.getReferencePrice(), discount);
            } else {
                throw new BusinessException("Un costo estimado requiere un costo unitario o un descuento");
            }
        }

        layer.setCostType(InventoryCostType.ESTIMATED);
        layer.setUnitCost(estimatedCost);
    }

    private void estimateByDiscount(InventoryCostLayer layer, BigDecimal discountPercentage) {
        if (layer.getCostType() == InventoryCostType.REAL) {
            throw new BusinessException("La capa ya posee un costo real confirmado");
        }

        BigDecimal discount = calculator.percentage(discountPercentage);
        layer.setCostType(InventoryCostType.ESTIMATED);
        layer.setDiscountPercentage(discount);
        layer.setUnitCost(calculator.estimateFromDiscount(layer.getReferencePrice(), discount));
    }
}
