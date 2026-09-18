package com.travel_system.backend_app.model.enums;

public enum StudentTravelRouteStopStatus {
    EXPECTED,
    APPROACHING,
    REACHED,
    MISSED,
    INVALID_ROUTE,
    CANCELLED
}

/*
* EXPECTED: Viagem está ativa mas ainda não entrou na região de monitoriamento. Veículo <= 4km
* APPROACHING: Veículo <= de 2.5km do Ponto de Parada. Representa aproximação
* REACHED: Veículo <= 50M do Ponto de Parada (alcaçou o ponto). Representa desembarque se compatível com a evidência
* MISSED: Veículo passou pelo ponto sem desembarque identificado (caso não haja evidência de desembarque)
* INVALID_ROUTE: Ponto de Parada não pertence à rota/viagem esperada (usado para alertar o aluno via notificação)
* CANCELLED: associação deixou de ser válida ou viagem cancelada
* */
