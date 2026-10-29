package com.travel_system.backend_app.service.strategies.leave_customer;

import com.travel_system.backend_app.exceptions.ResourceInUseException;
import com.travel_system.backend_app.interfaces.LeaveCustomerStrategy;
import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.CnhRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.HealthDataCleanupService;
import com.travel_system.backend_app.service.RegistrationDataCleanupService;
import com.travel_system.backend_app.service.SetupAuthenticationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DriverLeaveCustomerStrategy implements LeaveCustomerStrategy {

    private final DriverRepository driverRepository;
    private final TravelRepository travelRepository;
    private final CnhRepository cnhRepository;

    private final RegistrationDataCleanupService registrationDataCleanupService;
    private final HealthDataCleanupService healthDataCleanupService;

    private static final List<TravelStatus> BLOCKING_STATUSES = List.of(TravelStatus.SCHEDULED, TravelStatus.PENDING, TravelStatus.TRAVELLING);

    public DriverLeaveCustomerStrategy(DriverRepository driverRepository, SetupAuthenticationService setupAuthenticationService, TravelRepository travelRepository, CnhRepository cnhRepository, RegistrationDataCleanupService registrationDataCleanupService, HealthDataCleanupService healthDataCleanupService) {
        this.driverRepository = driverRepository;
        this.travelRepository = travelRepository;
        this.cnhRepository = cnhRepository;
        this.registrationDataCleanupService = registrationDataCleanupService;
        this.healthDataCleanupService = healthDataCleanupService;
    }

    @Override
    public boolean supports(UserAccountType type) {
        return type == UserAccountType.DRIVER;
    }

    @Override
    public void validateLeave(UserAccount account) {
        Driver driver = findDriver(account);

        if (travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), BLOCKING_STATUSES)) {
            throw new ResourceInUseException("Não é possível sair do espaço: você possui viagem agendada, pendente ou em andamento.");
        }
    }

    @Override
    public void unlinkFromCustomer(UserAccount account) {
        Driver driver = findDriver(account);
        Cnh cnh = driver.getCnh();

        healthDataCleanupService.deleteAllByOwner(new OwnerBuilt(OwnerType.DRIVER, driver.getId()));
        registrationDataCleanupService.deleteAllByOwner(new OwnerBuilt(OwnerType.DRIVER, driver.getId()));

        driver.setUserAccount(null);
        driver.setStatus(GeneralStatus.INACTIVE);
        driver.setLeftAt(Instant.now());

        // dados pessoais saem (nome, sobrenome e foto ficam p/ o histórico)
        driver.setTelephone(null);
        driver.setBirthdate(null);
        driver.setAddress(null);
        driver.setCnh(null);

        if (cnh != null) {
            cnhRepository.delete(cnh);
        }
    }

    private Driver findDriver(UserAccount account) {
        return driverRepository.findByUserAccountId(account.getId()).orElseThrow(() -> new AccessDeniedException("Motorista não encontrado"));
    }
}
