package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RouteStopResponseMapper {

    RouteStopResponseDTO toDTO(RouteStop routeStop);

    @Mapping(target = "routeStopId", source = "routeStop.id")
    @Mapping(target = "stopName", source = "routeStop.name")
    @Mapping(target = "travelDirection", source = "travelDirection")
    @Mapping(target = "stopSequence", source = "sequence")
    @Mapping(target = "isOptionalStop", source = "optionalSpot")
    RouteStopAssignmentResponseDTO toAssignmentDTO(RouteStopAssignment assignment);

    // Mapeia individualmente cada elemento da coleção 'routeStopAssignments' para o UUID da parada
    default UUID map(RouteStopAssignment assignment) {
        if (assignment == null || assignment.getRouteStop() == null) {
            return null;
        }
        return assignment.getRouteStop().getId();
    }

    default Set<UUID> mapStudentToIds(Set<Student> students) {
        if (students.isEmpty()) {
            return null;
        }

        return students.stream().map(Student::getId).collect(Collectors.toSet());
    }
}
