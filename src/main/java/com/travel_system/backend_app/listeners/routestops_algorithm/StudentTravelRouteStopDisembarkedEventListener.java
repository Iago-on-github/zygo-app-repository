package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.StudentTravelRouteStopDisembarkedEvent;
import com.travel_system.backend_app.infrastructure.TenantFilterAspect;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StudentTravelRouteStopDisembarkedEventListener {

    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;
    private final TenantFilterAspect tenantFilterAspect;

    public StudentTravelRouteStopDisembarkedEventListener(StudentTravelRouteStopRepository studentTravelRouteStopRepository, TenantFilterAspect tenantFilterAspect) {
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
        this.tenantFilterAspect = tenantFilterAspect;
    }

    /*
     * realiza a persistência de forma async confirmando que o desembarque do estudante para o routeStop foi realizado com sucesso
     * */

    @Async
    @EventListener
    public void handleDisembarkedStudentTravelRouteStop(StudentTravelRouteStopDisembarkedEvent event) {

        // ativa o filtro do customerId antes de qualquer acesso ao banco
        tenantFilterAspect.applyFilter();

        studentTravelRouteStopRepository.updateStatus(
                event.studentTravelId(),
                event.routeStopId(),
                event.studentTravelRouteStopStatus(),
                event.lastValidatedAt(),
                event.reachedAt());
    }
}
