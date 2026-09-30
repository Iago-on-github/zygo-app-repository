package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultProfileDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.ResponsibleAdultResponseDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResponsibleAdultRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userAccount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "name", source = "responsibleAdultAccept.name")
    @Mapping(target = "lastName", source = "responsibleAdultAccept.lastName")
    @Mapping(target = "cpf", source = "responsibleAdultAccept.cpf")
    @Mapping(target = "telephone", source = "responsibleAdultAccept.telephone")
    @Mapping(target = "birthdate", source = "responsibleAdultAccept.birthdate")
    @Mapping(target = "responsibleAdultType", source = "responsibleAdultInvitation.responsibleAdultType")
    ResponsibleAdult toEntity(ResponsibleAdultProfileDTO profileDTO);

    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "userAccount.email", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ResponsibleAdult toUpdate(ResponsibleAdultUpdateDTO dto, @MappingTarget ResponsibleAdult responsibleAdult);


}
