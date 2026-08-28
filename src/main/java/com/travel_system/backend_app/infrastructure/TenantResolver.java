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

    // UUID neutro reservado para tarefas do sistema, rotas públicas e PlatformAdmin
    private static final UUID SYSTEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // chamado para capturar o UUID do cliente a cada operação no banco
    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID currentTenant = getCurrentTenant();

        System.out.println("currentTenant: " + currentTenant);

        return (currentTenant != null) ? currentTenant : SYSTEM_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // hibernate garante se a sessão atual pertence ao mesmo tenant
        return true;
    }
}
