package com.travel_system.backend_app.service.strategies.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorProfileDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.service.AdministratorService;
import com.travel_system.backend_app.service.InvitationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class AdministratorInvitationProfileStrategy {

    private final AdministratorService administratorService;
    private final InvitationService invitationService;

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public AdministratorInvitationProfileStrategy(AdministratorService administratorService, InvitationService invitationService, Validator validator, ObjectMapper objectMapper) {
        this.administratorService = administratorService;
        this.invitationService = invitationService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    // envia o convite para o adm
    public InvitationResponseDTO sendAdministratorInvitation(AdministratorInvitationRequestDTO dto) {
        return invitationService.sendInvitation(dto.email(), dto.admInvitation());
    }

    // adm aceita o convite
    public InvitationAcceptResponseDTO acceptAdministratorInvitation(UUID invitationId, AdministratorAcceptDTO admAcceptDTO) {
        return invitationService.acceptInvitation(invitationId, TargetUserType.ADMINISTRATOR,
                ((invitation, account) -> createProfile(invitation, account, admAcceptDTO)));
    }

    private UUID createProfile(Invitation invitation, UserAccount userAccount, AdministratorAcceptDTO admAcceptDTO) {
        AdministratorInvitationDTO admInvitationDTO = readProfileData(invitation.getProfileData());

        AdministratorProfileDTO admProfileDTO = new AdministratorProfileDTO(admInvitationDTO, admAcceptDTO);

        validateOrThrow(admProfileDTO);

        Administrator adm = administratorService.createForExistingAccount(userAccount, invitation.getCustomerId(), admProfileDTO);

        return adm.getId();
    }

    // executa as mesmas anotações que o @Valid executaria no controller, só que sobre um objeto montado dentro do service
    private void validateOrThrow(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private AdministratorInvitationDTO readProfileData(String profileData) {
        try {
            return objectMapper.readValue(profileData, AdministratorInvitationDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("profileData inválido no convite", e);
        }
    }

}
