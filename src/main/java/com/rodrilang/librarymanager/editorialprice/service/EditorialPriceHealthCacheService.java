package com.rodrilang.librarymanager.editorialprice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class EditorialPriceHealthCacheService {

    public static final String SUMMARY_CACHE = "editorialPriceHealthSummary";

    private final CacheManager cacheManager;

    public void evictSummary() {
        Cache cache = cacheManager.getCache(SUMMARY_CACHE);
        if (cache != null) cache.clear();
    }

    public void evictSummaryAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            evictSummary();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictSummary();
            }
        });
    }
}