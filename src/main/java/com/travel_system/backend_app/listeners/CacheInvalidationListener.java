package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.CustomerCacheInvalidationEvent;
import com.travel_system.backend_app.events.OwnerCacheInvalidationEvent;
import com.travel_system.backend_app.service.AccessRestrictionService;
import com.travel_system.backend_app.service.RegistrationStatusProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CacheInvalidationListener {

    private final RegistrationStatusProvider registrationStatusProvider;
    private final AccessRestrictionService accessRestrictionService;

    public CacheInvalidationListener(RegistrationStatusProvider registrationStatusProvider, AccessRestrictionService accessRestrictionService) {
        this.registrationStatusProvider = registrationStatusProvider;
        this.accessRestrictionService = accessRestrictionService;
    }

    /*
    * precisa rodar de forma síncrona pois é limpeza de cache de operação 'unica' no redis
    * roda sempre pós commit
    * roda sempre com fallbackExecution ativo, caso algum outro evento rode seja publicado fora de transição o listener ainda deve rodar
    * */

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictOwnerCache(OwnerCacheInvalidationEvent event) {
        registrationStatusProvider.evictOwner(event.customerId(), event.ownerType(), event.ownerId());
        accessRestrictionService.evictOwner(event.customerId(), event.ownerId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evictCustomerCache(CustomerCacheInvalidationEvent event) {
        registrationStatusProvider.evictCustomer(event.customerId());
        accessRestrictionService.evictCustomer(event.customerId());
    }

}
