package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.ClearRestrictionCacheEvent;
import com.travel_system.backend_app.resolvers.RestrictionSubjectResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ClearRestrictionCacheListener {

    private final RestrictionSubjectResolver restrictionSubjectResolver;

    public ClearRestrictionCacheListener(RestrictionSubjectResolver restrictionSubjectResolver) {
        this.restrictionSubjectResolver = restrictionSubjectResolver;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictCache(ClearRestrictionCacheEvent event) {
        restrictionSubjectResolver.evict(event.userAccountId());
    }
}
