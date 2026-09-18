package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.ResponsibleAdultResponseDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ResponsibleAdultRequestMapper {

    @Mapping(target = "userAccount.email", source = "email")
    ResponsibleAdult toEntity(ResponsibleAdultRequestDTO dto);

    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "userAccount.email", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ResponsibleAdult toUpdate(ResponsibleAdultUpdateDTO dto, @MappingTarget ResponsibleAdult responsibleAdult);


}
