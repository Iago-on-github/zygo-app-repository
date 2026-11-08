package com.travel_system.backend_app.model.enums;

// situação da renovação de uma matrícula em carência
public enum RenewalProgressStatus {
    NOT_STARTED,    // nenhuma renovação aberta
    DRAFT,          // começou e não enviou
    UNDER_REVIEW    // enviada
}
