package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.CustomerHoliday;
import com.travel_system.backend_app.model.dtos.response.CustomerHolidayResponseDTO;
import com.travel_system.backend_app.utils.StorageUrlMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerHolidayResponseMapper {

    @Mapping(target = "holidayDate", source = "date")
    CustomerHolidayResponseDTO toDTO(CustomerHoliday holiday);
}
