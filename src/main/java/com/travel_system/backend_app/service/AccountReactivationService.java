package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.AccountReactivatedEvent;
import com.travel_system.backend_app.events.StatusCacheInvalidationEvent;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/*
* responsável por gerenciar a reativação de um usuário (com transação ativa)
* */

@Service
public class AccountReactivationService {
    private final Logger log = LoggerFactory.getLogger(AccountReactivationService.class);

    private final UserAccountRepository userAccountRepository;
    private final TransportRegistrationRepository transportRegistrationRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final CustomerRepository customerRepository;

    private final CustomerTimeService customerTimeService;

    private final ApplicationEventPublisher eventPublisher;

    public AccountReactivationService(UserAccountRepository userAccountRepository, TransportRegistrationRepository transportRegistrationRepository, StudentRepository studentRepository, DriverRepository driverRepository, CustomerRepository customerRepository, CustomerTimeService customerTimeService, ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.customerRepository = customerRepository;
        this.customerTimeService = customerTimeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void reactivate(UUID userAccountId) {
        if (userAccountId == null) {
            return;
        }

        // limpa o inactiveAt via update transacional
        int rowsAffected = userAccountRepository.clearInactiveAtByUserId(userAccountId);

        if (rowsAffected > 0) {
            UserAccountType userType = userAccountRepository.findUserTypeByUserId(userAccountId, List.of(UserAccountType.STUDENT, UserAccountType.DRIVER));

            // se é um estudante ou motorista, tbm age na matrícula
            if (userType != null && userType != UserAccountType.UNASSIGNED) {
                OwnerBuilt ownerBuilt = resolveOwner(userAccountId, userType);

                if (ownerBuilt != null) {
                    // age sobre a matrícula para reativar
                    int rowsUpdated = transportRegistrationRepository.reactivateByOwnerId(ownerBuilt.id(), TransportRegistrationStatus.ACTIVE, TransportRegistrationStatus.INACTIVE);

                    if (rowsUpdated > 0) {
                        UUID customerIdByOwnerId = transportRegistrationRepository.findCustomerIdByOwnerId(ownerBuilt.id(), TransportRegistrationStatus.CLOSED);

                        // limpa o cache remanescente dos titulares jaq houve mudança de matrícula no banco
                        eventPublisher.publishEvent(new StatusCacheInvalidationEvent(customerIdByOwnerId, ownerBuilt));
                    }
                }
            }

            // publica evento sinalzando que houve reativação do usuário
            eventPublisher.publishEvent(new AccountReactivatedEvent(userAccountId, customerTimeService.instant()));
        }
    }

    private OwnerBuilt resolveOwner(UUID userAccountId, UserAccountType userType) {
        if (userType == UserAccountType.STUDENT) {
            UUID studentId = studentRepository.findIdByUserAccountId(userAccountId)
                    .orElse(null);

            if (studentId == null) {
                return null;
            }

            return new OwnerBuilt(OwnerType.STUDENT, studentId);
        }

        UUID driverId = driverRepository.findIdByUserAccountId(userAccountId)
                .orElse(null);

        if (driverId == null) {
            return null;
        }

        return new OwnerBuilt(OwnerType.DRIVER, driverId);
    }

}
