package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.StudentTravelRouteStopDisembarkedEvent;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StudentTravelRouteStopDisembarkedEventListener {

    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;

    public StudentTravelRouteStopDisembarkedEventListener(StudentTravelRouteStopRepository studentTravelRouteStopRepository) {
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
    }

    /*
     * realiza a persistência de forma async confirmando que o desembarque do estudante para o routeStop foi realizado com sucesso
     * */

    @Async
    @EventListener
    public void handleDisembarkedStudentTravelRouteStop(StudentTravelRouteStopDisembarkedEvent event) {

        studentTravelRouteStopRepository.updateStatus(
                event.studentTravelId(),
                event.routeStopId(),
                event.studentTravelRouteStopStatus(),
                event.lastValidatedAt(),
                event.reachedAt());
    }
}
