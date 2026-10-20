package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.dtos.response.HealthDeclarationResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HealthDeclarationResponseMapper {

    @Mapping(target = "ownerId", source = "studentId")
    @Mapping(target = "ownerId", source = "driverId")
    @Mapping(target = "customerTermResponse", source = "customerTerm")
    HealthDeclarationResponseDTO toDTO(HealthDeclaration declaration);
}
