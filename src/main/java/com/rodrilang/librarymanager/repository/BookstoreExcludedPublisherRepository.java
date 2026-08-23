package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.BookstoreExcludedPublisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookstoreExcludedPublisherRepository extends JpaRepository<BookstoreExcludedPublisher, Long> {

    boolean existsByBookstoreIdAndPublisherId(Long bookstoreId, Long publisherId);

    long deleteByBookstoreIdAndPublisherId(Long bookstoreId, Long publisherId);
}