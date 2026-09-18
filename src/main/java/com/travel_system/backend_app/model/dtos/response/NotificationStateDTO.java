package com.travel_system.backend_app.model.dtos.response;

public record NotificationStateDTO(String zone, String lastDistanceNotified, String lastNotificationAt, String timeStamp) {
}
