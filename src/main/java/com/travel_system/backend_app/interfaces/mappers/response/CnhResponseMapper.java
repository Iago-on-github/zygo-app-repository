package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.dtos.response.CnhResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CnhResponseMapper {

    CnhResponseDTO toDTO(Cnh cnh);
}
