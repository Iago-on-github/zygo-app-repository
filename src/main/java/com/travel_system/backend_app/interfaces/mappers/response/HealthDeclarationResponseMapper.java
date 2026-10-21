package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.dtos.response.HealthDeclarationResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface HealthDeclarationResponseMapper {

    @Mapping(target = "ownerId", source = "declaration", qualifiedByName = "resolveOwnerId")
    @Mapping(target = "customerTermResponse", source = "declaration.customerTerm")
    HealthDeclarationResponseDTO toDTO(HealthDeclaration declaration);

    @Named("resolveOwnerId")
    default UUID resolveOwnerId(HealthDeclaration declaration) {
        return declaration.getStudentId() != null ? declaration.getStudentId() : declaration.getDriverId();
    }
}
