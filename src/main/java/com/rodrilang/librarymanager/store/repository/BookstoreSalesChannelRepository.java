package com.rodrilang.librarymanager.store.repository;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.model.BookstoreSalesChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface BookstoreSalesChannelRepository extends JpaRepository<BookstoreSalesChannel, Long> {
    Optional<BookstoreSalesChannel> findByBookstoreIdAndChannel(Long bookstoreId, SalesChannelType channel);
    List<BookstoreSalesChannel> findAllByBookstoreId(Long bookstoreId);
}
