package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.HealthDeclaration;
import com.travel_system.backend_app.model.dtos.request.HealthDeclarationRequestDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HealthDeclarationRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    HealthDeclaration toEntity(HealthDeclarationRequestDTO dto);
}
