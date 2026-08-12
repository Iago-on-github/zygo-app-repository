package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface RouteStopResponseMapper {

    @Mapping(target = "studentIds", source = "students.id")
    @Mapping(target = "customerId", source = "customer.id")
    RouteStopResponseDTO toDTO(RouteStop routeStop);

    // Mapeia individualmente cada elemento da coleção 'routeStopAssignments' para o UUID da parada
    default UUID map(RouteStopAssignment assignment) {
        if (assignment == null || assignment.getRouteStop() == null) {
            return null;
        }
        return assignment.getRouteStop().getId();
    }
}
