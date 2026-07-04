package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.model.Bookstore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookstoreRepository extends JpaRepository<Bookstore, Long> {

}
