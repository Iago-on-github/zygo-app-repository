package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.interfaces.SensitiveOperationData;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record PlatformAdministratorCreationRequestDTO(
        @Email(message = "Verifique a inserção do email")
        @NotBlank(message = "Campo 'Email' é obrigatório")
        String email,

        @Size(min = 12, max = 72, message = "A senha deve conter entre 12 e 72 caracteres")
        @NotBlank(message = "Campo 'Senha' é obrigatório")
        String password
)  {

}
