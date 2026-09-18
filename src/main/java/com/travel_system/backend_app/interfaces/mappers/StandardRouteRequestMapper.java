package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteUpdateDTO;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface StandardRouteRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "standardGeometry", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "routeStopAssignments", ignore = true)
    StandardRoute toEntity(StandardRouteRequestDTO requestDTO);

    @Mapping(target = "originLatitude", ignore = true)
    @Mapping(target = "originLongitude", ignore = true)
    @Mapping(target = "destinationLatitude", ignore = true)
    @Mapping(target = "destinationLongitude", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void standardRouteUpdateFromDTO(StandardRouteUpdateDTO updateDTO, @MappingTarget StandardRoute standardRoute);
}
