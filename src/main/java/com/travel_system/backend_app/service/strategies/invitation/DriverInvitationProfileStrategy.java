package com.travel_system.backend_app.service.strategies.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.driver.DriverProfileDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.service.DriverService;
import com.travel_system.backend_app.service.InvitationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class DriverInvitationProfileStrategy {

    private final DriverService driverService;
    private final InvitationService invitationService;

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public DriverInvitationProfileStrategy(DriverService driverService, InvitationService invitationService, Validator validator, ObjectMapper objectMapper) {
        this.driverService = driverService;
        this.invitationService = invitationService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    // envia o convite para o driver
    public InvitationResponseDTO sendDriverInvitation(String email, DriverInvitationDTO driverInvitationDTO) {
        return invitationService.sendInvitation(email, driverInvitationDTO);
    }

    // driver aceita o convite
    public InvitationAcceptResponseDTO acceptDriverInvitation(UUID invitationId, DriverAcceptDTO driverAcceptDTO) {
        return invitationService.acceptInvitation(invitationId, TargetUserType.DRIVER,
                ((invitation, account) -> createProfile(invitation, account, driverAcceptDTO)));
    }

    private UUID createProfile(Invitation invitation, UserAccount userAccount, DriverAcceptDTO driverAcceptDTO) {
        DriverInvitationDTO driverInvitationDTO = readProfileData(invitation.getProfileData());

        DriverProfileDTO driverProfileDTO = new DriverProfileDTO(driverAcceptDTO, driverInvitationDTO);

        validateOrThrow(driverProfileDTO);

        Driver driver = driverService.createForExistingAccount(userAccount, invitation.getCustomerId(), driverProfileDTO);

        return driver.getId();
    }

    // executa as mesmas anotações que o @Valid executaria no controller, só que sobre um objeto montado dentro do service
    private void validateOrThrow(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private DriverInvitationDTO readProfileData(String profileData) {
        try {
            return objectMapper.readValue(profileData, DriverInvitationDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("profileData inválido no convite", e);
        }
    }

}
