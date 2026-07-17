package com.edushift.modules.admin.invoicing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class B2BInvoiceCronJob {

    private final B2BInvoiceService invoiceService;

    @Scheduled(cron = "0 0 1 1 * *")
    public void emitMonthly() {
        log.info("[b2b-cron] starting monthly invoice emission...");
        invoiceService.emitMonthly();
        log.info("[b2b-cron] monthly invoice emission completed");
    }

    @Scheduled(cron = "0 0 9 16 * *")
    public void applyDunning() {
        log.info("[b2b-cron] starting dunning process...");
        invoiceService.applyDunning();
        log.info("[b2b-cron] dunning process completed");
    }
}
