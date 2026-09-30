package com.travel_system.backend_app.service.strategies.invitation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.InvitationAcceptResponseDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.response.InvitationResponseDTO;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.service.InvitationService;
import com.travel_system.backend_app.service.StudentService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class StudentInvitationProfileStrategy {

    private final StudentService studentService;
    private final InvitationService invitationService;

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public StudentInvitationProfileStrategy(StudentService studentService, InvitationService invitationService, Validator validator, ObjectMapper objectMapper) {
        this.studentService = studentService;
        this.invitationService = invitationService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    // envia convite especificamente para o estudante
    public InvitationResponseDTO sendStudentInvitation(String email, StudentInvitationDTO dto) {
        return invitationService.sendInvitation(email, dto);
    }

    // realiza ação de aceite do estudante
    public InvitationAcceptResponseDTO acceptStudentInvitation(UUID invitationId, StudentAcceptDTO dto) {
        return invitationService.acceptInvitation(invitationId, TargetUserType.STUDENT,
                ((invitation, account) -> createProfile(invitation, account, dto)));
    }

    private UUID createProfile(Invitation invitation, UserAccount userAccount, StudentAcceptDTO studentAcceptDTO) {
        StudentInvitationDTO studentInvitationDTO = readProfileData(invitation.getProfileData());

        StudentProfileDTO studentProfileDTO = new StudentProfileDTO(studentInvitationDTO, studentAcceptDTO);

        validateOrThrow(studentProfileDTO);

        Student student = studentService.createForExistingAccount(userAccount, invitation.getCustomerId(), studentProfileDTO);

        return student.getId();
    }

    private StudentInvitationDTO readProfileData(String profileData) {
        try {
            return objectMapper.readValue(profileData, StudentInvitationDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("profileData inválido no convite", e);
        }
    }

    // executa as mesmas anotações que o @Valid executaria no controller, só que sobre um objeto montado dentro do service
    private void validateOrThrow(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
