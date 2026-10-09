package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.TravelNotificationService;
import com.travel_system.backend_app.service.TravelService;
import com.travel_system.backend_app.utils.DateTimeFormats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TravelJobs {
    private final Logger log = LoggerFactory.getLogger(TravelJobs.class);

    private final TravelRepository travelRepository;

    private final TravelService travelService;
    private final DateTimeFormats dateTimeFormats;
    private final TravelNotificationService travelNotificationService;

    public TravelJobs(TravelRepository travelRepository, TravelService travelService, DateTimeFormats dateTimeFormats, TravelNotificationService travelNotificationService) {
        this.travelRepository = travelRepository;
        this.travelService = travelService;
        this.dateTimeFormats = dateTimeFormats;
        this.travelNotificationService = travelNotificationService;
    }


    @Scheduled(fixedDelay = 30_000)
    public void scheduledTripJob() {
        List<UUID> scheduledTravels;

        try {
            scheduledTravels = travelService.getDueTravels();
        } catch (Exception e) {
            log.error("[scheduledTripJob] Erro ao buscar viagens agendadas", e);
            return;
        }

        for (UUID dueTravelId : scheduledTravels) {
            try {
                travelService.startTravel(dueTravelId);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("[scheduledTripJob] Conflito de concorrência na viagem {}", dueTravelId, e);
            } catch (Exception e) {
                log.error("[scheduledTripJob] Erro ao iniciar a viagem {}", dueTravelId, e);
            }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void checkTripsForUpcomingRemindersJob() {
        List<UUID> travelsId;

        try {
            travelsId = travelService.getScheduledTravelsWithinTimeWindow();
        } catch (Exception e) {
            log.error("[checkTripsForUpcomingRemindersJob] Erro ao buscar viagens agendadas", e);
            return;
        }

        travelRepository.findAllById(travelsId).forEach(travel -> {
            try {
                // manda notificações de agendamento p/ cada viagem
                travelNotificationService.sendScheduleRemindersTravelStart(travel);
            } catch (Exception e) {
                log.error("[checkTripsForUpcomingRemindersJob] Erro ao processar lembretes da viagem {}", travel.getId(), e);
            }
        });
    }

}
