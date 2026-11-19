package com.travel_system.backend_app.events;

import java.util.UUID;

/*
 * implementada em eventos onde altera dados em que a matrícula usa para funcionar (como grace, window, etc), formalizando que o cache deve ser limpo
 * */

public interface CustomerCacheInvalidationEvent {
    UUID customerId();
}
