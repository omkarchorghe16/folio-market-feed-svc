package com.stocknews.scheduler;

import com.stocknews.config.NasdaqProperties;
import com.stocknews.logging.ExceptionLog;
import com.stocknews.us.nasdaq.NasdaqSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily download of Nasdaq Trader's US symbol directory (Nasdaq Trader regenerates it every day).
 * Default: 06:00 America/New_York. Configure via nasdaq.schedule.cron-expression and nasdaq.schedule.timezone.
 * When nasdaq.sync-on-startup is true and the files are missing (first run, new container), they are
 * downloaded once at startup so the search endpoint works right away.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NasdaqSymbolSyncScheduler {
    private final NasdaqSymbolService nasdaqSymbolService;
    private final NasdaqProperties nasdaqProperties;

    @Scheduled(cron = "${nasdaq.schedule.cron-expression:0 0 6 * * *}",
            zone = "${nasdaq.schedule.timezone:America/New_York}")
    public void runDailySync() {
        log.info("Executing scheduled daily Nasdaq symbol sync");
        syncSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartupIfMissing() {
        if (nasdaqProperties.isSyncOnStartup() && !nasdaqSymbolService.hasStoredSymbolLists()) {
            log.info("Nasdaq symbol lists not found on disk; downloading them at startup");
            syncSafely();
        }
    }

    private void syncSafely() {
        try {
            nasdaqSymbolService.refreshSymbolLists();
        } catch (Exception exception) {
            log.error("Nasdaq symbol sync job failed:\n{}", ExceptionLog.stackTrace(exception));
        }
    }
}
