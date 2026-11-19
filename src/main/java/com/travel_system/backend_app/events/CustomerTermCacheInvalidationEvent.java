package com.travel_system.backend_app.events;

/*
* usado para limpar cache remanescente do customer
* afeta o registro, pois ele roda com base no termEnd do 'customerTerm', além disso, também pode afetar o próximo período
* sempre que existe alteração no termEnd é necessário limpar o cache remanescente do customer para ele
* */

import java.util.UUID;

public record CustomerTermCacheInvalidationEvent(UUID customerId) implements CustomerCacheInvalidationEvent {
}
