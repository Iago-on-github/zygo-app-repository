package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface StandardRouteResponseMapper {

    @Mapping(target = "travelPeriods", source = "travelPeriods")
    @Mapping(target = "routeStopAssignments", source = "routeStopAssignments")
    @Mapping(target = "customerId", source = "customerId")
    StandardRouteResponseDTO toDTO(StandardRoute standardRoute);

    // Mapeia cada RouteStopAssignment individual para o DTO aninhado RouteStopAssignmentResponseDTO
    @Mapping(target = "routeStopId", source = "routeStop.id")
    @Mapping(target = "stopName", source = "routeStop.name")
    @Mapping(target = "stopSequence", source = "sequence")
    @Mapping(target = "isOptionalStop", source = "optionalSpot")
    RouteStopAssignmentResponseDTO toRouteStopAssignmentDTO(RouteStopAssignment assignment);

    // Mapeia individualmente cada elemento da coleção 'routeStopAssignments' para o UUID da parada
    default UUID map(RouteStopAssignment assignment) {
        if (assignment == null || assignment.getRouteStop() == null) {
            return null;
        }
        return assignment.getRouteStop().getId();
    }
}
