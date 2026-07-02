package com.travel_system.backend_app.model.dtos.notifications;

import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PushNotificationCommandDTO(
        NotificationAudience notificationAudience,
        UUID userId,
        UUID customerId,
        UUID travelId,
        String title,
        String message,
        String link, // representa o redirecionamento do user ao clicar na notificação
        Priority priority,
        Map<String, String> data
) {
}
