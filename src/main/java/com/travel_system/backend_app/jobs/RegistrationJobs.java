package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.repository.RegistrationRequestRepository;
import com.travel_system.backend_app.service.RegistrationDocumentLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegistrationJobs {
    private static final Logger log = LoggerFactory.getLogger(RegistrationJobs.class);

    private final RegistrationRequestRepository registrationRequestRepository;

    private final RegistrationDocumentLifecycleService registrationDocumentLifecycleService;

    public RegistrationJobs(RegistrationRequestRepository registrationRequestRepository, RegistrationDocumentLifecycleService registrationDocumentLifecycleService) {
        this.registrationRequestRepository = registrationRequestRepository;
        this.registrationDocumentLifecycleService = registrationDocumentLifecycleService;
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "America/Sao_Paulo")
    public void processRegistrationsLifecycle() {
        try {
            registrationDocumentLifecycleService.processRegistrations();
        } catch (Exception e) {
            log.error("[processRegistrationsLifecycle] Falha ao processar os registros", e);
        }
    }


    // fora do horário de uso do app (madrugada)
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public void runDailyRegistrationJobs() {
        try {
            registrationDocumentLifecycleService.cancelAbandonedDrafts();
        } catch (Exception e) {
            log.error("[runDailyRegistrationJobs] Falha ao cancelar rascunhos abandonados", e);
        }
    }

}
