package com.travel_system.backend_app.service.strategies.leave_customer;

import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.interfaces.LeaveCustomerStrategy;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.InvitationRepository;
import com.travel_system.backend_app.service.InvitationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Component
public class AdministratorLeaveCustomerStrategy implements LeaveCustomerStrategy {

    private final AdministratorRepository administratorRepository;
    private final InvitationRepository invitationRepository;

    private final InvitationService invitationService;

    public AdministratorLeaveCustomerStrategy(AdministratorRepository administratorRepository, InvitationRepository invitationRepository, InvitationService invitationService) {
        this.administratorRepository = administratorRepository;
        this.invitationRepository = invitationRepository;
        this.invitationService = invitationService;
    }

    @Override
    public boolean supports(UserAccountType type) {
        return type == UserAccountType.ADMINISTRATOR;
    }

    @Override
    public void validateLeave(UserAccount account) {
       if (administratorRepository.countActiveAdministrators(GeneralStatus.ACTIVE) <= 1) {
            throw new DomainValidationException("Você é o único Administrador ativo no espaço, não é possível prosseguir");
       }
    }

    @Override
    public void unlinkFromCustomer(UserAccount account) {
        Administrator admin = findAdmin();
        Instant now = Instant.now();

        // convites pendentes enviados por este admin deixam de valer
        invitationRepository.findAllByInvitedByAndInvitationStatus(account.getId(), InvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke(now));

        admin.setUserAccount(null);
        admin.setStatus(GeneralStatus.INACTIVE);
        admin.setLeftAt(now);

        // dados pessoais (nome, sobrenome, cargo e foto ficam para o histórico)
        admin.setTelephone(null);
        admin.setCpf(null);
        admin.setBirthdate(null);
        admin.setAddress(null);
    }

    private Administrator findAdmin() {
        return administratorRepository.findByEmail(getAuthenticatedUserEmail()).orElseThrow(() -> new AccessDeniedException("Administrator não encontrado"));
    }
}
