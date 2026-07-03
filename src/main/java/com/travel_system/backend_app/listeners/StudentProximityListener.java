package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.StudentProximityEvents;
import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import com.travel_system.backend_app.service.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StudentProximityListener{
    private final NotificationService notificationService;

    public StudentProximityListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void handleStudentProximity(StudentProximityEvents proximityEvents) {
        notificationService.sendMessage(new StudentProximityNotificationMessage(
                proximityEvents.travelId(),
                proximityEvents.studentId(),
                proximityEvents.distance(),
                proximityEvents.zone(),
                proximityEvents.timestamp(),
                proximityEvents.alertType()));
    }

}
