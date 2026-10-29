package com.travel_system.backend_app.model.enums;

/*
 * itens que podem faltar no envio da solicitação de matrícula
 * */
public enum RegistrationMissingItem {
    IDENTITY_NUMBER,
    IDENTITY_ISSUER,
    IDENTITY_ISSUED_AT,
    IDENTITY_ISSUED_AT_IN_FUTURE,
    MOTHER_NAME,
    DOCUMENT_IDENTITY,
    DOCUMENT_INSTITUTION_PROOF,
    PHOTO,
    INSTITUTION
}
