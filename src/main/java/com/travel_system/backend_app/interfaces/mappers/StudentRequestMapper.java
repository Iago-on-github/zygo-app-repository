package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StudentRequestMapper {

    @Mapping(target = "userAccount.email", source = "email")
    @Mapping(target = "userAccount.password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void studentUpdateFromDTO(StudentUpdateDTO studentUpdateDTO, @MappingTarget Student student);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "name", source = "studentAccept.name")
    @Mapping(target = "lastName", source = "studentAccept.lastName")
    @Mapping(target = "telephone", source = "studentAccept.telephone")
    @Mapping(target = "birthdate", source = "studentAccept.birthdate")
    @Mapping(target = "studentShift", source = "studentInvitation.studentShifts")
    @Mapping(target = "institutionType", source = "studentInvitation.institutionType")
    @Mapping(target = "course", source = "studentInvitation.course")
    Student toEntity(StudentProfileDTO studentProfileDTO);
}
