package com.rodrilang.librarymanager.purchasing.receipt.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderItemRepository;
import com.rodrilang.librarymanager.purchasing.order.repository.PurchaseOrderRepository;
import com.rodrilang.librarymanager.purchasing.receipt.dto.request.CreateGoodsReceiptRequest;
import com.rodrilang.librarymanager.purchasing.receipt.dto.request.ScanGoodsReceiptItemRequest;
import com.rodrilang.librarymanager.purchasing.receipt.dto.request.UpdateGoodsReceiptRequest;
import com.rodrilang.librarymanager.purchasing.receipt.dto.request.UpsertGoodsReceiptItemRequest;
import com.rodrilang.librarymanager.purchasing.receipt.dto.response.GoodsReceiptDetailResponse;
import com.rodrilang.librarymanager.purchasing.receipt.dto.response.GoodsReceiptItemResponse;
import com.rodrilang.librarymanager.purchasing.receipt.dto.response.GoodsReceiptResponse;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceipt;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptItem;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptSource;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptStatus;
import com.rodrilang.librarymanager.purchasing.receipt.repository.GoodsReceiptItemRepository;
import com.rodrilang.librarymanager.purchasing.receipt.repository.GoodsReceiptRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.service.BookCatalogService;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptItemRepository itemRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderItemRepository orderItemRepository;
    private final ProviderRepository providerRepository;
    private final BookRepository bookRepository;
    private final BookCatalogService bookCatalogService;
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService inventoryStockService;
    private final IsbnService isbnService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Transactional
    public GoodsReceiptDetailResponse create(CreateGoodsReceiptRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        PurchaseOrder order = null;
        Provider provider = null;

        if (request.purchaseOrderId() != null) {
            order = orderRepository.findByIdAndBookstoreIdForUpdate(request.purchaseOrderId(), bookstoreId)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido seleccionado."));

            if (order.getStatus() != PurchaseOrderStatus.SENT
                    && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
                throw new BusinessException("Solo se puede recibir mercadería de un pedido enviado o parcialmente recibido.");
            }

            var existingDraft = receiptRepository
                    .findFirstByPurchaseOrderIdAndStatusOrderByIdDesc(order.getId(), GoodsReceiptStatus.DRAFT);
            if (existingDraft.isPresent()) {
                return detail(existingDraft.get());
            }

            provider = order.getProvider();
            if (request.providerId() != null && !request.providerId().equals(provider.getId())) {
                throw new BusinessException("El proveedor indicado no coincide con el proveedor del pedido.");
            }
        } else if (request.providerId() != null) {
            provider = requireProvider(request.providerId());
        }

        GoodsReceiptSource source = order != null
                ? GoodsReceiptSource.ORDER
                : hasDocument(request.documentType(), request.documentNumber())
                ? GoodsReceiptSource.DOCUMENT
                : GoodsReceiptSource.FREE;

        GoodsReceipt receipt = GoodsReceipt.builder()
                .bookstore(bookstore)
                .provider(provider)
                .purchaseOrder(order)
                .receiptNumber("TMP-" + UUID.randomUUID())
                .source(source)
                .status(GoodsReceiptStatus.DRAFT)
                .documentType(clean(request.documentType()))
                .documentNumber(clean(request.documentNumber()))
                .notes(clean(request.notes()))
                .build();

        receipt = receiptRepository.saveAndFlush(receipt);
        receipt.setReceiptNumber(generateReceiptNumber(receipt.getId()));

        if (order != null) {
            preloadOrderItems(receipt, order);
        }

        return detail(receipt);
    }

    @Transactional(readOnly = true)
    public Page<GoodsReceiptResponse> findAll(Long purchaseOrderId, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Page<GoodsReceipt> page = purchaseOrderId == null
                ? receiptRepository.findAllByBookstoreId(bookstoreId, pageable)
                : receiptRepository.findAllByBookstoreIdAndPurchaseOrderId(bookstoreId, purchaseOrderId, pageable);
        return page.map(this::summary);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDetailResponse findById(Long receiptId) {
        return detail(requireReceipt(receiptId));
    }

    @Transactional
    public GoodsReceiptDetailResponse update(Long receiptId, UpdateGoodsReceiptRequest request) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);

        if (receipt.getPurchaseOrder() == null) {
            Provider provider = request.providerId() == null ? null : requireProvider(request.providerId());
            receipt.setProvider(provider);
        } else if (request.providerId() != null
                && !request.providerId().equals(receipt.getPurchaseOrder().getProvider().getId())) {
            throw new BusinessException("El proveedor de una recepción vinculada a un pedido no puede cambiarse.");
        }

        receipt.setDocumentType(clean(request.documentType()));
        receipt.setDocumentNumber(clean(request.documentNumber()));
        receipt.setNotes(clean(request.notes()));
        receipt.setSource(receipt.getPurchaseOrder() != null
                ? GoodsReceiptSource.ORDER
                : hasDocument(receipt.getDocumentType(), receipt.getDocumentNumber())
                ? GoodsReceiptSource.DOCUMENT
                : GoodsReceiptSource.FREE);

        return detail(receipt);
    }

    @Transactional
    public GoodsReceiptDetailResponse upsertItem(Long receiptId, UpsertGoodsReceiptItemRequest request) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);
        BookCondition condition = request.condition() == null ? BookCondition.NEW : request.condition();
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new BusinessException("Libro no encontrado."));

        GoodsReceiptItem item = itemRepository.findByReceiptIdAndBookIdAndCondition(receiptId, book.getId(), condition)
                .orElseGet(() -> GoodsReceiptItem.builder()
                        .receipt(receipt)
                        .book(book)
                        .condition(condition)
                        .purchaseOrderItem(findMatchingOrderItem(receipt, book.getId()))
                        .expectedQuantity(expectedFor(receipt, book.getId()))
                        .scannedQuantity(0)
                        .receivedQuantity(0)
                        .build());

        item.setDocumentQuantity(request.documentQuantity());
        item.setReceivedQuantity(request.receivedQuantity());
        item.setNotes(clean(request.notes()));
        itemRepository.save(item);
        return detail(receipt);
    }

    @Transactional
    public GoodsReceiptDetailResponse scan(Long receiptId, ScanGoodsReceiptItemRequest request) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);
        ParsedIsbn parsed = isbnService.parse(request.code());
        if (!parsed.valid()) {
            throw new BusinessException("El código escaneado no es un ISBN válido.");
        }

        Book book = bookRepository.findByIsbn13(parsed.isbn13())
                .or(() -> parsed.isbn10() == null ? java.util.Optional.empty() : bookRepository.findByIsbn10(parsed.isbn10()))
                .orElseGet(() -> bookCatalogService.getOrCreateByIsbn(parsed.preferredIsbn()));

        GoodsReceiptItem item = itemRepository.findByReceiptIdAndBookIdAndCondition(receiptId, book.getId(), BookCondition.NEW)
                .orElseGet(() -> GoodsReceiptItem.builder()
                        .receipt(receipt)
                        .book(book)
                        .condition(BookCondition.NEW)
                        .purchaseOrderItem(findMatchingOrderItem(receipt, book.getId()))
                        .expectedQuantity(expectedFor(receipt, book.getId()))
                        .scannedQuantity(0)
                        .receivedQuantity(0)
                        .build());

        item.setScannedQuantity(item.getScannedQuantity() + 1);
        item.setReceivedQuantity(item.getReceivedQuantity() + 1);
        itemRepository.save(item);
        return detail(receipt);
    }

    @Transactional
    public GoodsReceiptDetailResponse removeItem(Long receiptId, Long itemId) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);
        GoodsReceiptItem item = itemRepository.findByIdAndReceiptId(itemId, receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el ítem de recepción."));

        if (item.getPurchaseOrderItem() != null) {
            item.setDocumentQuantity(null);
            item.setScannedQuantity(0);
            item.setReceivedQuantity(0);
            item.setNotes(null);
        } else {
            itemRepository.delete(item);
        }
        return detail(receipt);
    }

    @Transactional
    public GoodsReceiptDetailResponse confirm(Long receiptId) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);

        if (receipt.getPurchaseOrder() != null
                && receipt.getPurchaseOrder().getStatus() != PurchaseOrderStatus.SENT
                && receipt.getPurchaseOrder().getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("El pedido vinculado ya no admite nuevas recepciones.");
        }

        List<GoodsReceiptItem> items = itemRepository.findAllByReceiptIdOrderByIdAsc(receiptId);

        int total = items.stream()
                .mapToInt(item -> value(item.getReceivedQuantity()))
                .sum();

        if (total <= 0) {
            throw new BusinessException("La recepción debe tener al menos una unidad para confirmar.");
        }

        for (GoodsReceiptItem item : items) {
            int receivedQuantity = value(item.getReceivedQuantity());

            if (receivedQuantity <= 0) {
                continue;
            }

            /*
             * Al inventario ingresa TODO lo recibido físicamente,
             * incluso si supera la cantidad solicitada en el pedido.
             */
            Inventory inventory = findOrCreateInventory(receipt, item);

            inventoryStockService.changeStock(
                    inventory.getId(),
                    new InventoryStockChangeCommand(
                            receivedQuantity,
                            InventoryMovementType.ENTRY,
                            InventoryMovementSource.MANUAL,
                            InventoryMovementReferenceType.GOODS_RECEIPT,
                            receipt.getId().toString(),
                            "Recepción de mercadería " + receipt.getReceiptNumber()
                    )
            );

            /*
             * Pero al pedido sólo se le imputan como recibidas
             * las unidades que todavía estaban pendientes.
             *
             * Ejemplo:
             * pedido = 5
             * ya recibido = 0
             * recepción = 7
             *
             * stock += 7
             * pedido recibido = 5/5
             * excedente de recepción = 2
             */
            if (item.getPurchaseOrderItem() != null) {
                applyReceivedQuantityToOrderItem(
                        item.getPurchaseOrderItem(),
                        receivedQuantity
                );
            }
        }

        receipt.setStatus(GoodsReceiptStatus.CONFIRMED);
        receipt.setConfirmedAt(Instant.now());

        if (receipt.getPurchaseOrder() != null) {
            refreshOrderStatus(receipt.getPurchaseOrder());
        }

        return detail(receipt);
    }

    private void applyReceivedQuantityToOrderItem(
            PurchaseOrderItem orderItem,
            int receivedNow
    ) {
        int orderedQuantity = value(orderItem.getQuantity());
        int alreadyReceived = value(orderItem.getReceivedQuantity());

        int pendingQuantity = Math.max(
                orderedQuantity - alreadyReceived,
                0
        );

        if (pendingQuantity == 0) {
            return;
        }

        int quantityAppliedToOrder = Math.min(
                receivedNow,
                pendingQuantity
        );

        orderItem.setReceivedQuantity(
                alreadyReceived + quantityAppliedToOrder
        );
    }

    @Transactional
    public void cancel(Long receiptId) {
        GoodsReceipt receipt = requireDraftForUpdate(receiptId);
        receipt.setStatus(GoodsReceiptStatus.CANCELLED);
    }

    private void preloadOrderItems(GoodsReceipt receipt, PurchaseOrder order) {
        List<PurchaseOrderItem> orderItems = orderItemRepository.findAllByPurchaseOrderIdOrderByIdAsc(order.getId());
        for (PurchaseOrderItem orderItem : orderItems) {
            int remaining = Math.max(orderItem.getQuantity() - value(orderItem.getReceivedQuantity()), 0);
            if (remaining == 0) continue;
            itemRepository.save(GoodsReceiptItem.builder()
                    .receipt(receipt)
                    .book(orderItem.getBook())
                    .purchaseOrderItem(orderItem)
                    .condition(BookCondition.NEW)
                    .expectedQuantity(remaining)
                    .scannedQuantity(0)
                    .receivedQuantity(0)
                    .build());
        }
    }

    private PurchaseOrderItem findMatchingOrderItem(GoodsReceipt receipt, Long bookId) {
        if (receipt.getPurchaseOrder() == null) return null;
        return orderItemRepository.findAllByPurchaseOrderIdOrderByIdAsc(receipt.getPurchaseOrder().getId())
                .stream().filter(item -> item.getBook().getId().equals(bookId)).findFirst().orElse(null);
    }

    private Integer expectedFor(GoodsReceipt receipt, Long bookId) {
        PurchaseOrderItem orderItem = findMatchingOrderItem(receipt, bookId);
        return orderItem == null ? null : Math.max(orderItem.getQuantity() - value(orderItem.getReceivedQuantity()), 0);
    }

    private Inventory findOrCreateInventory(GoodsReceipt receipt, GoodsReceiptItem item) {
        return inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                        item.getBook().getId(), receipt.getBookstore().getId(), item.getCondition())
                .map(inventory -> {
                    if (!Boolean.TRUE.equals(inventory.getActive())) inventory.setActive(true);
                    return inventory;
                })
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .book(item.getBook())
                        .bookstore(receipt.getBookstore())
                        .condition(item.getCondition())
                        .stock(0)
                        .minimumStock(0)
                        .tiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED)
                        .tiendanubePriceSyncEnabled(false)
                        .active(true)
                        .build()));
    }

    private void refreshOrderStatus(PurchaseOrder order) {
        List<PurchaseOrderItem> items = orderItemRepository.findAllByPurchaseOrderIdOrderByIdAsc(order.getId());

        int ordered = items.stream()
                .mapToInt(item -> value(item.getQuantity()))
                .sum();

        int received = items.stream()
                .mapToInt(item -> value(item.getReceivedQuantity()))
                .sum();

        if (ordered > 0 && received >= ordered) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (received > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        } else {
            order.setStatus(PurchaseOrderStatus.SENT);
        }
    }

    private GoodsReceipt requireReceipt(Long id) {
        return receiptRepository.findByIdAndBookstoreId(id, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la recepción con ID: " + id));
    }

    private GoodsReceipt requireDraftForUpdate(Long id) {
        GoodsReceipt receipt = receiptRepository.findByIdAndBookstoreIdForUpdate(id, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la recepción con ID: " + id));
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw new BusinessException("Solo se puede modificar una recepción en borrador.");
        }
        return receipt;
    }

    private Provider requireProvider(Long id) {
        return providerRepository.findById(id)
                .filter(Provider::isPurchasable)
                .orElseThrow(() -> new BusinessException("El proveedor seleccionado no se encuentra activo."));
    }

    private GoodsReceiptDetailResponse detail(GoodsReceipt receipt) {
        List<GoodsReceiptItemResponse> items = itemRepository.findAllByReceiptIdOrderByIdAsc(receipt.getId())
                .stream().map(this::itemResponse).toList();
        return new GoodsReceiptDetailResponse(
                receipt.getId(), receipt.getReceiptNumber(), receipt.getSource(), receipt.getStatus(),
                receipt.getProvider() != null ? receipt.getProvider().getId() : null,
                receipt.getProvider() != null ? receipt.getProvider().getName() : null,
                receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getId() : null,
                receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getOrderNumber() : null,
                receipt.getDocumentType(), receipt.getDocumentNumber(), receipt.getNotes(),
                items.size(), items.stream().mapToInt(GoodsReceiptItemResponse::receivedQuantity).sum(),
                receipt.getCreatedAt(), receipt.getConfirmedAt(), items
        );
    }

    private GoodsReceiptResponse summary(GoodsReceipt receipt) {
        List<GoodsReceiptItem> items = itemRepository.findAllByReceiptIdOrderByIdAsc(receipt.getId());
        return new GoodsReceiptResponse(
                receipt.getId(), receipt.getReceiptNumber(), receipt.getSource(), receipt.getStatus(),
                receipt.getProvider() != null ? receipt.getProvider().getId() : null,
                receipt.getProvider() != null ? receipt.getProvider().getName() : null,
                receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getId() : null,
                receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getOrderNumber() : null,
                receipt.getDocumentType(), receipt.getDocumentNumber(), items.size(),
                items.stream().mapToInt(item -> value(item.getReceivedQuantity())).sum(),
                receipt.getCreatedAt(), receipt.getConfirmedAt()
        );
    }

    private GoodsReceiptItemResponse itemResponse(GoodsReceiptItem item) {
        Integer expected = item.getExpectedQuantity();
        Integer document = item.getDocumentQuantity();
        int received = value(item.getReceivedQuantity());
        return new GoodsReceiptItemResponse(
                item.getId(), item.getBook().getId(), item.getBook().getPreferredIsbn(), item.getBook().getTitle(),
                item.getBook().getCoverUrl(),
                item.getPurchaseOrderItem() != null ? item.getPurchaseOrderItem().getId() : null,
                item.getCondition(), expected, document, value(item.getScannedQuantity()), received,
                expected != null ? received - expected : null,
                document != null ? received - document : null,
                item.getNotes()
        );
    }

    private String generateReceiptNumber(Long id) {
        return "REC-%d-%06d".formatted(Year.now().getValue(), id);
    }

    private boolean hasDocument(String type, String number) {
        return clean(type) != null || clean(number) != null;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
