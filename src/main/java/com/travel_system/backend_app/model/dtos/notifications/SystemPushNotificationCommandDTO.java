package com.travel_system.backend_app.model.dtos.notifications;

import com.travel_system.backend_app.interfaces.PushNotificationContent;
import com.travel_system.backend_app.model.enums.SystemNotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;

import java.util.Map;
import java.util.UUID;

public record SystemPushNotificationCommandDTO(
        SystemNotificationAudience systemNotificationAudience,
        UUID customerId,
        UUID studentId,
        UUID adminId,
        UUID driverId,
        UUID userAccountId,
        String title,
        String message,
        String link,
        Priority priority,
        Map<String, String> data
) implements PushNotificationContent {
}
