package com.travel_system.backend_app.model.dtos.invitation.driver;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.model.enums.TargetUserType;

import javax.validation.constraints.*;
import java.time.LocalDate;

public record DriverAcceptDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @NotBlank(message = "O sobrenome é obrigatório")
        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @NotNull
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        String telephone,

        @NotNull
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate

) {

}
