package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.ClientSector;
import jakarta.validation.Valid;
import org.simpleframework.xml.core.Validate;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.UUID;

public record CustomerOperationDataRequestDTO(
        @NotBlank
        @Size(min = 4, max = 50, message = "O nome do Customer deve ter entre 4-50 caracteres")
        String name,

        @NotBlank
        @Size(min = 4, max = 50, message = "O slug do Customer deve ter entre 4-50 caracteres")
        String slug,

        @NotBlank
        @Size(min = 4, max = 100, message = "O nome legal do Customer deve ter entre 4-100 caracteres")
        String legalName,

        @NotBlank
        @Size(min = 14, max = 14, message = "O CNPJ deve ter 14 caracteres")
        String cnpj,

        @NotBlank
        @Email
        @Size(max = 30, message = "O email de contato deve ter até 30 caracteres")
        String contactEmail,

        @NotBlank
        @Size(min = 11, max = 11, message = "O telefone de contato deve ter até 11 caracteres")
        String contactTelephone,

        @NotNull
        UUID cityId,

        @NotNull
        ClientSector clientSector,

        @Valid
        AddressRequestDTO addressRequest
        ) {
}
