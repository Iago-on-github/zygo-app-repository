package com.travel_system.backend_app.model.enums;

// Enum destinado a separar notificações com base nos usuários que devem recebe-la

public enum NotificationAudience {
    STUDENT_ONLY,
    DRIVER_ONLY,
    ADMIN_ONLY,
    ALL_CUSTOMER_USERS,
    SPECIFIC_USER,
    TRAVEL_STUDENTS,
    EMBARKED_TRAVEL_STUDENTS
}
