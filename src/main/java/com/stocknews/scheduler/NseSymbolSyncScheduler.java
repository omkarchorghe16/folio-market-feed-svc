package com.stocknews.scheduler;

import com.stocknews.config.NseProperties;
import com.stocknews.india.nse.NseSymbolService;
import com.stocknews.logging.ExceptionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly download of NSE's equity and ETF symbol lists.
 * Default: Sunday 06:00 Asia/Kolkata. Configure via nse.schedule.cron-expression and nse.schedule.timezone.
 * When nse.sync-on-startup is true and the files are missing (first run, new container), they are
 * downloaded once at startup so the search endpoint works without waiting for Sunday.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NseSymbolSyncScheduler {
    private final NseSymbolService nseSymbolService;
    private final NseProperties nseProperties;

    @Scheduled(cron = "${nse.schedule.cron-expression:0 0 6 * * SUN}", zone = "${nse.schedule.timezone:Asia/Kolkata}")
    public void runWeeklySync() {
        log.info("Executing scheduled weekly NSE symbol sync");
        syncSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfMissing() {
        if (nseProperties.isSyncOnStartup() && !nseSymbolService.hasStoredSymbolLists()) {
            log.info("NSE symbol lists not found on disk; downloading them at startup");
            syncSafely();
        }
    }

    private void syncSafely() {
        try {
            nseSymbolService.refreshSymbolLists();
        } catch (Exception e) {
            log.error("NSE symbol sync job failed:\n{}", ExceptionLog.stackTrace(e));
        }
    }
}
