package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Institution;
import com.travel_system.backend_app.model.dtos.request.InstitutionRequestDTO;
import com.travel_system.backend_app.model.dtos.request.InstitutionUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InstitutionRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "courses", ignore = true)
    Institution toEntity(InstitutionRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "courses", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(InstitutionUpdateDTO dto, @MappingTarget Institution institution);
}
