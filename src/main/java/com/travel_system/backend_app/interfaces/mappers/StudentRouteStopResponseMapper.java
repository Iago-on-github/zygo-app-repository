package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopSimpleResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteSimpleResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface StudentRouteStopResponseMapper {

    @Mapping(target = "id", source = "assignment.routeStop.id")
    @Mapping(target = "name", source = "assignment.routeStop.name")
    @Mapping(target = "description", source = "assignment.routeStop.description")
    @Mapping(target = "latitude", source = "assignment.routeStop.latitude")
    @Mapping(target = "longitude", source = "assignment.routeStop.longitude")
    @Mapping(target = "status", source = "assignment.routeStop.status")
    @Mapping(target = "customerId", source = "assignment.routeStop.customer.id")
    @Mapping(target = "createdAt", source = "assignment.routeStop.createdAt")
    @Mapping(target = "updatedAt", source = "assignment.routeStop.updatedAt")
    @Mapping(target = "travelPeriod", source = "assignment.standardRoute.travelPeriod")
    @Mapping(target = "routeStopAssignments", source = "assignment.routeStop.routeStopAssignments")
    @Mapping(target = "routeStop", source = "assignment.routeStop")
    @Mapping(target = "standardRoute", source = "assignment.standardRoute")
    @Mapping(target = "studentIds", source = "studentIds")
    StudentRouteStopAssociateResponseDTO toDTO(StudentRouteStopAssignment assignment, Set<UUID> studentIds);

    StandardRouteSimpleResponseDTO map(StandardRoute standardRoute);

    RouteStopSimpleResponseDTO map(RouteStop routeStop);

    default Set<RouteStopAssignmentResponseDTO> mapAssignments(List<RouteStopAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return Set.of();
        }
        return assignments.stream()
                .map(assignment -> new RouteStopAssignmentResponseDTO(
                        assignment.getRouteStop().getId(),
                        assignment.getRouteStop().getName(),
                        assignment.getSequence(),
                        assignment.isOptionalSpot()

                ))
                .collect(Collectors.toSet());
    }

    // conversão da lista de assignments para Set de UUIDs de estudantes
    default Set<UUID> mapStudentIds(List<StudentRouteStopAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return Set.of();
        }
        return assignments.stream()
                .filter(a -> a.getStudent() != null)
                .map(a -> a.getStudent().getId())
                .collect(Collectors.toSet());
    }
}
