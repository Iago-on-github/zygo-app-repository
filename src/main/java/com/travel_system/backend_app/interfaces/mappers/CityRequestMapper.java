package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.dtos.request.CityRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CityRequestMapper {
    City toEntity(CityRequestDTO dto);
}
