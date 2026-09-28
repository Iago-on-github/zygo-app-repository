package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.enums.Priority;

import java.util.Map;

public interface PushNotificationContent {
    String title();
    String message();
    String link();
    Priority priority();
    Map<String, String> data();
}
