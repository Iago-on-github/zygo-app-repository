package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.config.constants.SchedulerConstraints;
import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.service.CustomerNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.config.constants.SchedulerConstraints.SCHEDULER_ZONE;

@Component
public class CustomerJobs {
    private final Logger log = LoggerFactory.getLogger(CustomerJobs.class);

    private final CustomerRepository customerRepository;

    private final CustomerNotificationService customerNotificationService;

    public CustomerJobs(CustomerRepository customerRepository, CustomerNotificationService customerNotificationService) {
        this.customerRepository = customerRepository;
        this.customerNotificationService = customerNotificationService;
    }

    /*
    * envia lembretes a cada 1d (seg-sex) para os administradores do espaço lembrando de modifcar/confirmar o timezone deles
    * envia apenas para customers cujo existem ao menos 1 admin ativo
    * envia apenas para customers cujo ainda não confirmaram o timezone
    * */
    @Scheduled(cron = "0 0 10 * * MON-FRI", zone = SchedulerConstraints.SCHEDULER_ZONE)
    public void sendTimeZoneNotification() {
        List<UUID> allByTimeZoneConfirmedFalse = customerRepository.findAllByTimeZoneConfirmedFalse();

        try {
            // notifica cada administrador a partir do customer
            customerNotificationService.sendConfirmTimezoneNotificationToAdmin(allByTimeZoneConfirmedFalse);
        } catch (Exception e) {
            log.warn("Ocorreu um erro durante o envio da notificação ao firebase", e);
        }

    }

}
