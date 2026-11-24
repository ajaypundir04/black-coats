package com.blackcoat.api.chat.service;

import com.blackcoat.api.common.exception.QuotaExceededException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    public static final UUID ANONYMOUS_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final EntityManager entityManager;
    private final int dailyLimit;
    private final int monthlyLimit;

    public QuotaService(
        EntityManager entityManager,
        @Value("${blackcoat.quota.daily-limit:10}") int dailyLimit,
        @Value("${blackcoat.quota.monthly-limit:100}") int monthlyLimit
    ) {
        this.entityManager = entityManager;
        this.dailyLimit = dailyLimit;
        this.monthlyLimit = monthlyLimit;
    }

    /**
     * Atomically reserves 1 query quota.
     * Uses PostgreSQL atomic function to guarantee safety under concurrent requests.
     */
    @Transactional
    public void reserveQuota(UUID userId) {
        if (ANONYMOUS_USER_ID.equals(userId)) {
            // Anonymous guest session: permit browsing without foreign-key failure or exhaustion
            log.debug("Permitting query for anonymous guest session: {}", userId);
            return;
        }

        String sql = "SELECT reserve_user_quota(:userId, :dailyLimit, :monthlyLimit)";
        Boolean reserved = (Boolean) entityManager.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("dailyLimit", dailyLimit)
            .setParameter("monthlyLimit", monthlyLimit)
            .getSingleResult();

        if (reserved == null || !reserved) {
            log.warn("Quota exceeded for user: {}", userId);
            throw new QuotaExceededException(
                "You have reached your free query limit (" + dailyLimit + "/day or " + monthlyLimit + "/month)."
            );
        }
        log.debug("Successfully reserved 1 query quota for user: {}", userId);
    }

    /**
     * Atomically refunds 1 reserved query quota if the downstream pipeline failed
     * or if retrieval yielded no sources.
     */
    @Transactional
    public void refundQuota(UUID userId) {
        if (ANONYMOUS_USER_ID.equals(userId)) {
            return;
        }

        try {
            String sql = "SELECT refund_user_quota(:userId)";
            entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getSingleResult();
            log.info("Refunded 1 query quota for user: {}", userId);
        } catch (Exception ex) {
            log.error("Failed to refund quota for user {}: {}", userId, ex.getMessage());
        }
    }
}
