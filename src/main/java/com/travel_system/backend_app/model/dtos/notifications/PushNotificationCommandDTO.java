package com.travel_system.backend_app.model.dtos.notifications;

import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.Shift;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PushNotificationCommandDTO(
        NotificationAudience notificationAudience,
        UUID customerId,
        UUID travelId,
        UUID studentId,
        UUID driverId,
        Shift shift,
        String title,
        String message,
        String link,
        Priority priority,
        Map<String, String> data
) {
}

/* GUIDE dos IDs das Domain Entites
*
* customerId = notifica todos os usuários de um tipo específico dentro do customer.
* O tipo é determinado pelo próprio NotificationAudience (CUSTOMER_STUDENTS, CUSTOMER_DRIVERS,
* CUSTOMER_ADMINS, CUSTOMER_RESPONSIBLES), não por um ID adicional.
*
* travelId = notifica com base em uma viagem específca
*
* studentId = notifica o responsável (ResponsibleAdult) vinculado a esse estudante específico.
* Usado em eventos pontuais de um aluno (ex.: embarque/desembarque), diferente de travelId,
* que notifica responsáveis de todos os alunos da viagem.
* */