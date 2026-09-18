package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.CancelledStudentTravelRouteStopEvent;
import com.travel_system.backend_app.infrastructure.TenantFilterAspect;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import com.travel_system.backend_app.service.TravelTrackingNotificationService;
import com.travel_system.backend_app.service.TravelTrackingStaticCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CancelledStudentTravelRouteStopListener {
    private final Logger log = LoggerFactory.getLogger(CancelledStudentTravelRouteStopListener.class);

    private final TravelTrackingStaticCacheService travelTrackingStaticCacheService;
    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;
    private final TravelTrackingNotificationService travelTrackingNotificationService;
    private final TenantFilterAspect tenantFilterAspect;

    public CancelledStudentTravelRouteStopListener(TravelTrackingStaticCacheService travelTrackingStaticCacheService, StudentTravelRouteStopRepository studentTravelRouteStopRepository, TravelTrackingNotificationService travelTrackingNotificationService, TenantFilterAspect tenantFilterAspect) {
        this.travelTrackingStaticCacheService = travelTrackingStaticCacheService;
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
        this.travelTrackingNotificationService = travelTrackingNotificationService;
        this.tenantFilterAspect = tenantFilterAspect;
    }

    @EventListener
    @Async
    public void handleCancelledStudentTravelRouteStop(CancelledStudentTravelRouteStopEvent event) {
        if (event == null) {
            log.warn("[handleCancelledStudentTravelRouteStop - Evento nulo recebido, ignorando processamento");
            return;
        }

        // ativa o filtro do customerId antes de qualquer acesso ao banco
        tenantFilterAspect.applyFilter();

        studentTravelRouteStopRepository.updateCancelledStatus(event.studentTravelId(), event.routeStopId(), event.studentTravelRouteStopStatus(), event.lastValidatedAt());

        // notificação
        travelTrackingNotificationService.sendCancelledRouteStopNotification(event);

        // limpa o cache estático do tracking da viagem p/ o estudante
        travelTrackingStaticCacheService.removeStudentTravelTrackingCache(event.travelId(), event.studentTravelId());
    }
}
