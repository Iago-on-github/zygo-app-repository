package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Address;
import com.travel_system.backend_app.model.Institution;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StudentRequestMapper {

    @Mapping(target = "userAccount.email", source = "email")
    @Mapping(target = "address", source = "addressUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void studentUpdateFromDTO(StudentUpdateDTO dto, @MappingTarget Student student);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "studentAccept.name")
    @Mapping(target = "cpf", source = "studentAccept.cpf")
    @Mapping(target = "address", source = "studentAccept.addressRequest")
    @Mapping(target = "lastName", source = "studentAccept.lastName")
    @Mapping(target = "telephone", source = "studentAccept.telephone")
    @Mapping(target = "birthdate", source = "studentAccept.birthdate")
    Student toEntity(StudentProfileDTO studentProfileDTO);

    // mapeamentos auxiliares
    @Mapping(target = "id", ignore = true)
    Address toAddressUpdate(AddressUpdateDTO dto);

    @Mapping(target = "id", ignore = true)
    Address toAddress(AddressRequestDTO dto);

/*    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "courses", ignore = true)
    Institution toInstitutionUpdate(InstitutionUpdateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    Institution toInstitution(InstitutionRequestDTO dto);*/
}
