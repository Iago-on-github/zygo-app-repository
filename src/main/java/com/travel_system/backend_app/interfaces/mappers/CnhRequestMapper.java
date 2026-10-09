package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.dtos.request.CnhRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CnhUpdateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CnhRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "driver", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    Cnh toEntity(CnhRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "cnhCategories", ignore = true)
    @Mapping(target = "driver", ignore = true)
    void updateFromDTO(CnhUpdateDTO dto, @MappingTarget Cnh cnh);
}
