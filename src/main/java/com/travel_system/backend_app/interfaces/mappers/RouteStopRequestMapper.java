package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.dtos.request.RouteStopRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface RouteStopRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "customer", ignore = true)
    RouteStop toEntity(RouteStopRequestDTO routeStopRequestDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void routeStopUpdateDTO(RouteStopUpdateDTO routeStopUpdateDTO, @MappingTarget RouteStop routeStop);
}
