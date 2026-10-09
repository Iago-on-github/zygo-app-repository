package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Institution;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InstitutionResponseMapper {

    InstitutionResponseDTO toDTO(Institution institution);
}
