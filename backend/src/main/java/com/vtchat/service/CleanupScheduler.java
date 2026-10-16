package com.vtchat.service;

import com.vtchat.repository.OtpTokenRepository;
import com.vtchat.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final OtpTokenRepository otpRepo;
    private final RefreshTokenRepository refreshRepo;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeStaleRows() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        int otps = otpRepo.deleteCreatedBefore(cutoff);
        int tokens = refreshRepo.deleteStale(cutoff);
        log.info("Cleanup removed {} OTP rows and {} refresh tokens", otps, tokens);
    }
}
