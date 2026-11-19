package com.travel_system.backend_app.model.enums;

/*
* categorias nas quais os acessos são permitidos ou bloqueados
*
* 'isAlwaysAllowed' cumpre o papel de informar quais categs estão livres de propósito
* um verifier roda em tempo de compilação para verificar se há alguma categ sem nenhum nível ou marcador
*
* */

public enum AccessCategory {
    TRAVEL_BOARDING(false), // entrar em viagens
    TRANSPORT_OPERATIONS(false), // operações de viagens diversas (rotas, paradas, históricos, gerenciamento)
    DOCUMENTS(false), // documentos de forma geral

    IN_PROGRESS(true), // concluir o que já começou
    OWN_ACCOUNT(true), // dados e conta próprios, perfil, regulações, lgpd
    ADMINISTRATOR(true), // administradores (customer ou plataforma)
    BACKEND_SYSTEM(true); // operações do próprio sistema

    private final boolean isAlwaysAllowed;

    AccessCategory(boolean isAlwaysAllowed) {
        this.isAlwaysAllowed = isAlwaysAllowed;
    }

    public boolean isAlwaysAllowed() {
        return isAlwaysAllowed;
    }
}
