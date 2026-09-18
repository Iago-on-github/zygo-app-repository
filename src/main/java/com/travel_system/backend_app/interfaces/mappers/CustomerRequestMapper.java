package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CustomerRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "city", ignore = true)
    Customer toEntity(CustomerRequestDTO customerRequestDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDTO(CustomerUpdateDTO customerUpdateDTO, @MappingTarget Customer customer);

}
