package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.Size;

public record AddressUpdateDTO(
        @Size(min = 5, max = 50, message = "O nome da rua deve ter entre 5 e 50 caracteres")
        String street,

        Integer number,

        @Size(min = 5, max = 50, message = "O nome do bairro deve ter entre 5 e 50 caracteres")
        String neighborhood,

        @Size(min = 5, max = 50, message = "O nome da cidade deve ter entre 5 e 50 caracteres")
        String city,

        @Size(min = 8, max = 8, message = "O cep deve ter 8 caracteres")
        String cep,

        @Size(min = 5, max = 50, message = "O complemento deve ter entre 5 e 50 caracteres")
        String complement
) {
}
