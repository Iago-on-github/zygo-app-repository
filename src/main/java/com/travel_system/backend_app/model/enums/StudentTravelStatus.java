package com.travel_system.backend_app.model.enums;

// status que o sistema usa para validar se ainda está na viagem
public enum StudentTravelStatus {
    ACTIVE, // ativo na viagem
    BOARD, // fisicamente embarcado (pelo algoritmo de auto-connected)
    AWAY_FROM_BUS, // estado fisicamente distante do ônbus
    AUTO_DISCONNECTED, // desconectado da viagem (pelo algoritmo de auto-disconnected)
    LEFT // saiu manualmente da viagem
}
