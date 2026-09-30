package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.invitation.admin.AdministratorProfileDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdministratorRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobTitle", source = "administratorInvitation.jobTitle")
    @Mapping(target = "name", source = "administratorAccept.name")
    @Mapping(target = "lastName", source = "administratorAccept.lastName")
    @Mapping(target = "cpf", source = "administratorAccept.cpf")
    @Mapping(target = "birthdate", source = "administratorAccept.birthdate")
    @Mapping(target = "telephone", source = "administratorAccept.telephone")
    Administrator toEntity(AdministratorProfileDTO profileDTO);

    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "userAccount.email", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobTitle", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "cpf", ignore = true)
    @Mapping(target = "birthdate", ignore = true)
    @Mapping(target = "telephone", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void administratorUpdateFromDTO(AdministratorUpdateDTO admUpdateDTO, @MappingTarget Administrator admEntity);
}
