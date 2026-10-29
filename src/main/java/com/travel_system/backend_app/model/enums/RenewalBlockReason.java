package com.travel_system.backend_app.model.enums;

/*
 * motivos pelos quais a renovação não pode ser iniciada
 * */
public enum RenewalBlockReason {
    NOT_ACTIVE,
    OPEN_REQUEST,
    OUTSIDE_WINDOW,
    GRACE_EXPIRED,
    NEXT_TERM_MISSING
}

/*
* GUIDE
* NOT_ACTIVE = matrícula não está ACTIVE (PENDING, INACTIVE)
* OPEN_REQUEST = já existe uma solicitação em andamento
* OUTSIDE_WINDOW = a janela de renovação ainda não abriu
* GRACE_EXPIRED = a carência acabou (a matrícula será encerrada pelo job)
* NEXT_TERM_MISSING = o próximo período ainda não foi cadastrado
*
* */