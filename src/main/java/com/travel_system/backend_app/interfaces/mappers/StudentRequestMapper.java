package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StudentRequestMapper {

    @Mapping(target = "userAccount.email", source = "email")
    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "studentRelationshipType", ignore = true)
    @Mapping(target = "studentTravels", ignore = true)
    @Mapping(target = "studentRouteStopAssignments", ignore = true)
    @Mapping(target = "responsibleAdult", ignore = true)
    @Mapping(target = "birthdate", ignore = true)
    @Mapping(target = "studentShift", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void studentUpdateFromDTO(StudentUpdateDTO studentUpdateDTO, @MappingTarget Student student);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "studentRelationshipType", ignore = true)
    @Mapping(target = "studentTravels", ignore = true)
    @Mapping(target = "studentRouteStopAssignments", ignore = true)
    @Mapping(target = "responsibleAdult", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", source = "studentAccept.name")
    @Mapping(target = "lastName", source = "studentAccept.lastName")
    @Mapping(target = "telephone", source = "studentAccept.telephone")
    @Mapping(target = "birthdate", source = "studentAccept.birthdate")
    @Mapping(target = "studentShift", source = "studentInvitation.studentShifts")
    @Mapping(target = "institutionType", source = "studentInvitation.institutionType")
    @Mapping(target = "course", source = "studentInvitation.course")
    Student toEntity(StudentProfileDTO studentProfileDTO);
}
