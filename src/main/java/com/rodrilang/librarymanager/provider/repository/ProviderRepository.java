package com.rodrilang.librarymanager.provider.repository;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    @Query("""
            select provider
            from Provider provider
            where provider.active = true
              and (:type is null or provider.type = :type)
              and (
                    provider.verificationStatus = :verifiedStatus
                    or (
                        provider.verificationStatus = :pendingStatus
                        and :bookstoreId is not null
                        and provider.createdByBookstoreId = :bookstoreId
                    )
                  )
            order by provider.name asc
            """)
    List<Provider> findAvailableForBookstore(
            @Param("type") ProviderType type,
            @Param("bookstoreId") Long bookstoreId,
            @Param("verifiedStatus") ProviderVerificationStatus verifiedStatus,
            @Param("pendingStatus") ProviderVerificationStatus pendingStatus
    );

    @Query("""
            select provider
            from Provider provider
            where provider.type = :type
              and (:status is null or provider.verificationStatus = :status)
            """)
    Page<Provider> findForAdminReview(
            @Param("type") ProviderType type,
            @Param("status") ProviderVerificationStatus status,
            Pageable pageable
    );

    Optional<Provider> findFirstByActiveTrueAndTypeAndVerificationStatusAndTaxIdIgnoreCase(
            ProviderType type,
            ProviderVerificationStatus verificationStatus,
            String taxId
    );

    Optional<Provider> findFirstByActiveTrueAndTypeAndVerificationStatusAndCreatedByBookstoreIdAndTaxIdIgnoreCase(
            ProviderType type,
            ProviderVerificationStatus verificationStatus,
            Long createdByBookstoreId,
            String taxId
    );

    Optional<Provider> findFirstByActiveTrueAndTypeAndNameIgnoreCase(
            ProviderType type,
            String name
    );

    boolean existsByCodeIgnoreCase(String code);
}
