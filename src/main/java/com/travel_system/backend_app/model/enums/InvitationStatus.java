package com.travel_system.backend_app.model.enums;

public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED,
    CANCELLED
}

//CANCELLED é para o cancelamento automático quando outro convite é aceito,
// separá-lo de REVOKED deixa a auditoria clara.