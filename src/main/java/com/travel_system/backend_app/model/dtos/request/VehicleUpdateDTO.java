package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.VehicleType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public record VehicleUpdateDTO(
        @Size(min = 3, max = 25, message = "O numero do veículo deve ter entre 3 e 25 caracteres")
        String vehicleNumber,

        VehicleType vehicleType,

        @Size(max = 25, message = "A cor do veículo pode ter no máximo 25 caracteres")
        String color,

        @Size(min = 7, max = 7, message = "A placa do veículo deve ter 7 caracteres")
        String numberPlate
) {
}
