package com.travel_system.backend_app.infrastructure;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

@Component
public class TenantResolver implements CurrentTenantIdentifierResolver<UUID> {

    // chamado para capturar o UUID do cliente a cada operação no banco
    @Override
    public UUID resolveCurrentTenantIdentifier() {
        /*
        * caso seja null, o hibernate omite o customer_id na consulta
        * lida como rota pública ou plataform_admin
        * */
        return getCurrentTenant();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // hibernate garante se a sessão atual pertence ao mesmo tenant
        return true;
    }
}
