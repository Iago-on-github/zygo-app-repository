package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.HealthCondition;
import com.travel_system.backend_app.model.dtos.request.HealthConditionRequestDTO;
import com.travel_system.backend_app.model.dtos.request.HealthConditionUpdateDTO;
import jakarta.validation.Valid;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HealthConditionRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "declaration", ignore = true)
    HealthCondition toEntity(HealthConditionRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "declaration", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(@Valid HealthConditionUpdateDTO healthConditionUpdateDTO, @MappingTarget HealthCondition condition);
}
