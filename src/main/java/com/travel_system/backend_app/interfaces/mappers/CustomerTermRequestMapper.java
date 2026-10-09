package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.CustomerTerm;
import com.travel_system.backend_app.model.dtos.request.CustomerTermRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerTermUpdateDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedSourcePolicy = ReportingPolicy.ERROR)
public interface CustomerTermRequestMapper {

    @Mapping(target = "id", ignore = true)
    CustomerTerm toEntity(CustomerTermRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(CustomerTermUpdateDTO dto, @MappingTarget CustomerTerm customerTerm);
}

