package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.CustomerTimeZoneChangedEvent;
import com.travel_system.backend_app.utils.CustomerZoneCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CustomerTimeZoneChangedListener {

    private final CustomerZoneCache customerZoneCache;

    public CustomerTimeZoneChangedListener(CustomerZoneCache customerZoneCache) {
        this.customerZoneCache = customerZoneCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictCacheByKey(CustomerTimeZoneChangedEvent event) {
        customerZoneCache.evict(event.customerIdKey());
    }
}
