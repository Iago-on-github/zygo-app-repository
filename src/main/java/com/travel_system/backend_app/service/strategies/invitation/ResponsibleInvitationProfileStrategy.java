package com.travel_system.backend_app.service.strategies.invitation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultProfileDTO;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleInvitationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.service.InvitationService;
import com.travel_system.backend_app.service.ResponsibleAdultService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class ResponsibleInvitationProfileStrategy {

    private final ResponsibleAdultService responsibleAdultService;
    private final InvitationService invitationService;

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ResponsibleInvitationProfileStrategy(ResponsibleAdultService responsibleAdultService, InvitationService invitationService, ObjectMapper objectMapper, Validator validator) {
        this.responsibleAdultService = responsibleAdultService;
        this.invitationService = invitationService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    // envia convite para o responsible
    public InvitationResponseDTO sendResponsibleAdultInvitation(ResponsibleInvitationRequestDTO dto) {
        return invitationService.sendInvitation(dto.email(), dto.responsibleAdultInvitation());
    }

    // responsible aceita convite
    public InvitationAcceptResponseDTO acceptResponsibleAdultInvitation(UUID invitationId, ResponsibleAdultAcceptDTO responsibleAdultAcceptDTO) {
        return invitationService.acceptInvitation(invitationId, TargetUserType.RESPONSIBLE_ADULT,
                ((invitation, account) -> createProfile(invitation, account, responsibleAdultAcceptDTO)));
    }

    private UUID createProfile(Invitation invitation, UserAccount userAccount, ResponsibleAdultAcceptDTO responsibleAdultAcceptDTO) {
        ResponsibleAdultInvitationDTO responsibleAdultInvitationDTO = readProfileData(invitation.getProfileData());

        ResponsibleAdultProfileDTO responsibleAdultProfileDTO = new ResponsibleAdultProfileDTO(responsibleAdultInvitationDTO, responsibleAdultAcceptDTO);

        validateOrThrow(responsibleAdultProfileDTO);

        ResponsibleAdult responsibleAdult = responsibleAdultService.createForExistingAccount(userAccount, invitation.getCustomerId(), responsibleAdultProfileDTO);

        return responsibleAdult.getId();
    }

    private ResponsibleAdultInvitationDTO readProfileData(String profileData) {
        try {
            return objectMapper.readValue(profileData, ResponsibleAdultInvitationDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("profileData inválido no convite", e);
        }
    }

    private void validateOrThrow(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

}
