package com.travel_system.backend_app.model.enums;

public enum HealthPendingItemType {
    CONSENT_MISSING,
    DECLARATION_MISSING,
    DECLARATION_OUTDATED,
    CONDITION_WITHOUT_DOCUMENT,
    DOCUMENT_REJECTED,
    DOCUMENT_EXPIRED
}

/*
* CONSENT_MISSING = termo de consentimento não aceito
* DECLARATION_MISSING = declaração de saúde nunca preenchida
* DECLARATION_OUTDATED = declaração de um período letivo anterior
* CONDITION_WITHOUT_DOCUMENT = condição que exige comprovação sem documento ativo
* DOCUMENT_REJECTED = documento rejeitado ainda não substituído
* DOCUMENT_EXPIRED = documento vencido ainda não substituído
* */
