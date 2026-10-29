package com.travel_system.backend_app.model.enums;
/*
 * motivos pelos quais a alteração não pode ser iniciada
 * */

public enum UpdateBlockReason {
    NOT_ACTIVE,
    OPEN_REQUEST,
    RENEWAL_PERIOD
}

/*
* GUIDE
* NOT_ACTIVE = matrícula não está ACTIVE (PENDING: basta corrigir o rascunho da INITIAL)
* OPEN_REQUEST = já existe uma solicitação em andamento
* RENEWAL_PERIOD = janela de renovação aberta ou carência: os dados são atualizados na renovação
* */