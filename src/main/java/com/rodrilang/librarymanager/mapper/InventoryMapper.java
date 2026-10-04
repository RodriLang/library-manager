package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookProviderResponse;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.preference.dto.response.PreferredProviderResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", uses = {BookMapper.class})
public abstract class InventoryMapper {

    protected BookMapper bookMapper;

    @Autowired
    public void setBookMapper(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Mapping(target = "id", source = "inventory.id")
    @Mapping(target = "active", source = "inventory.active")
    @Mapping(target = "createdAt", source = "inventory.createdAt")
    @Mapping(target = "updatedAt", source = "inventory.updatedAt")
    @Mapping(target = "preferredProvider", source = "preferredProvider")
    @Mapping(target = "book", expression = "java(toBookDetailResponse(inventory, providers))")
    @Mapping(target = "salePrice", expression = "java(currentPrice != null ? currentPrice.getAmount() : null)")
    @Mapping(target = "currentPriceEffectiveFrom", expression = "java(currentPrice != null ? currentPrice.getEffectiveFrom() : null)")
    @Mapping(target = "currentPriceLastConfirmedAt", expression = "java(currentPrice != null ? currentPrice.getLastConfirmedAt() : null)")
    @Mapping(target = "currentPriceLastConfirmedSource", expression = "java(currentPrice != null ? currentPrice.getLastConfirmedSource() : null)")
    @Mapping(target = "nextSalePrice", expression = "java(nextPrice != null ? nextPrice.getAmount() : null)")
    @Mapping(target = "nextPriceEffectiveFrom", expression = "java(nextPrice != null ? nextPrice.getEffectiveFrom() : null)")
    public abstract InventoryDetailResponse toDetailResponse(
            Inventory inventory,
            InventoryPrice currentPrice,
            InventoryPrice nextPrice,
            List<BookProviderResponse> providers,
            PreferredProviderResponse preferredProvider
    );

    protected BookDetailResponse toBookDetailResponse(
            Inventory inventory,
            List<BookProviderResponse> providers
    ) {
        if (inventory == null || inventory.getBook() == null) {
            return null;
        }
        return bookMapper.toDetailResponse(inventory.getBook(), providers);
    }

    @Mapping(target = "id", source = "inventory.id")
    @Mapping(target = "bookId", source = "inventory.book.id")
    @Mapping(target = "isbn", expression = "java(inventory.getBook().getPreferredIsbn())")
    @Mapping(target = "title", source = "inventory.book.title")
    @Mapping(target = "publisherName", expression = "java(inventory.getBook().getPublisher() != null ? inventory.getBook().getPublisher().getName() : null)")
    @Mapping(target = "coverUrl", source = "inventory.book.coverUrl")
    @Mapping(target = "active", source = "inventory.active")
    @Mapping(target = "salePrice", expression = "java(currentPrice != null ? currentPrice.getAmount() : null)")
    @Mapping(target = "currentPriceEffectiveFrom", expression = "java(currentPrice != null ? currentPrice.getEffectiveFrom() : null)")
    @Mapping(target = "currentPriceLastConfirmedAt", expression = "java(currentPrice != null ? currentPrice.getLastConfirmedAt() : null)")
    @Mapping(target = "currentPriceLastConfirmedSource", expression = "java(currentPrice != null ? currentPrice.getLastConfirmedSource() : null)")
    @Mapping(target = "nextSalePrice", expression = "java(nextPrice != null ? nextPrice.getAmount() : null)")
    @Mapping(target = "nextPriceEffectiveFrom", expression = "java(nextPrice != null ? nextPrice.getEffectiveFrom() : null)")
    @Mapping(target = "authorNames", expression = "java(toAuthorNames(inventory))")
    public abstract InventorySummaryResponse toSummaryResponse(
            Inventory inventory,
            InventoryPrice currentPrice,
            InventoryPrice nextPrice
    );

    protected List<String> toAuthorNames(Inventory inventory) {
        if (inventory == null || inventory.getBook() == null || inventory.getBook().getAuthors() == null) {
            return List.of();
        }
        return inventory.getBook().getAuthors().stream()
                .map(Author::getName)
                .sorted()
                .toList();
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "book", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bookstore", ignore = true)
    @Mapping(target = "tiendanubeStatus", ignore = true)
    public abstract void updateEntity(UpdateInventoryRequest request, @MappingTarget Inventory inventory);
}
