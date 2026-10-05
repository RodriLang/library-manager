package com.rodrilang.librarymanager.purchasing.receipt.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {
    @EntityGraph(attributePaths = {"book", "purchaseOrderItem"})
    List<GoodsReceiptItem> findAllByReceiptIdOrderByIdAsc(Long receiptId);

    Optional<GoodsReceiptItem> findByIdAndReceiptId(Long id, Long receiptId);

    Optional<GoodsReceiptItem> findByReceiptIdAndBookIdAndCondition(Long receiptId, Long bookId, BookCondition condition);
}
