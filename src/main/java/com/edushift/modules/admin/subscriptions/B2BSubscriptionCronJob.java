package com.edushift.modules.admin.subscriptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class B2BSubscriptionCronJob {

    private final B2BSubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 4 * * *")
    public void expireStale() {
        log.info("[b2b-cron] starting stale subscription expiration...");
        subscriptionService.expireStale();
        log.info("[b2b-cron] stale subscription expiration completed");
    }
}
