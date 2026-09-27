package com.course.springlearning.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks tokens past their expiry time as expired and revoked, also the ones that are never sent again
 * (a request with an expired token already does this for that token).
 */
@Component
public class ExpiredTokenJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenJob.class);

    private final TokenService tokenService;

    public ExpiredTokenJob(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(fixedDelayString = "${app.security.jwt.expired-cleanup-interval}",
            initialDelayString = "${app.security.jwt.expired-cleanup-interval}")
    public void revokeExpiredTokens() {
        try {
            int count = tokenService.revokeAllExpired();
            if (count > 0) {
                log.info("Marked {} expired token(s) as expired and revoked", count);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to mark expired tokens", e);
        }
    }
}
