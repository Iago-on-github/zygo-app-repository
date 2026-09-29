package com.travel_system.backend_app.service.strategies.invitation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.Invitation;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.StudentAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.StudentInvitationDTO;
import com.travel_system.backend_app.model.dtos.invitation.StudentProfileDTO;
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

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public StudentInvitationProfileStrategy(StudentService studentService, Validator validator, ObjectMapper objectMapper) {
        this.studentService = studentService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public UUID createProfile(Invitation invitation, UserAccount userAccount, StudentAcceptDTO studentAcceptDTO) {
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
