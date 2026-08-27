package com.travel_system.backend_app.infrastructure;

import java.util.UUID;

public class TenantContext {

    private final static ThreadLocal<UUID> threadLocalId = new ThreadLocal<>();

    // não permite instanciação
    private TenantContext() {
        throw new UnsupportedOperationException("Não é possível instanciar essa class.");
    }

    // insere o uuid extraído na thread
    public static void setCurrentTenant(UUID extractCustomerId) {
        threadLocalId.set(extractCustomerId);
    }

    // retorna o uuid
    public static UUID getCurrentTenant() {
        return threadLocalId.get();
    }

    // remove
    public static void removeCurrentTenant() {
        threadLocalId.remove();
    }

}
