package com.travel_system.backend_app.model.enums;

public enum ResourceType {
    DOCUMENT,
    HEALTH_DECLARATION,
    TRIP_HEALTH_SUMMARY,
    TRANSPORT_REGISTRATION,
    REGISTRATION_DOCUMENT,
    REGISTRATION_PHOTO
}

/*
* GUIDE
*
* DOCUMENT: documento em si do user
* HEALTH_DECLARATION: documento de saúde declarada pelo user
* TRIP_HEALTH_SUMMARY: acesso aos docs de saude do user através durante a viagem (feito pelo motorista)
* TRANSPORT_REGISTRATION: acesso aos dados de registro de transporte (matrícula, etc)
* REGISTRATION_DOCUMENT: acesso aos documentos de registro
* REGISTRATION_PHOTO: acesso as fotos 3x4 de registro
* * */
