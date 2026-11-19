package com.urlshortener.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically deletes links that expired longer ago than the retention window. Expired links are
 * kept for a while first so they answer 410 Gone (clearer than 404) and their stats stay readable.
 *
 * <p>Multi-instance note: every node runs this; the DELETE is idempotent so that is safe, merely
 * redundant. ShedLock (or a single scheduler instance) would remove the duplicate work.
 */
@Component
@ConditionalOnProperty(prefix = "app.links.purge", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ExpiredLinkPurger {

    private static final Logger log = LoggerFactory.getLogger(ExpiredLinkPurger.class);

    private final ShortLinkService service;

    public ExpiredLinkPurger(ShortLinkService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.links.purge.cron:0 15 3 * * *}", zone = "UTC")
    public void purge() {
        int removed = service.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired links", removed);
        }
    }
}
