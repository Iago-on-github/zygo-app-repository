package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.CustomerHoliday;
import com.travel_system.backend_app.model.dtos.request.CustomerCalendarRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerCalendarUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerHolidayRequestMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "description", source = "description")
    @Mapping(target = "date", source = "holidayDate")
    @Mapping(target = "shifts", source = "shifts")
    CustomerHoliday toEntity(CustomerCalendarRequestDTO dto);

    @Mapping(target = "description", source = "description")
    @Mapping(target = "shifts", source = "shifts")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, ignoreByDefault = true)
    void updateFromDTO(CustomerCalendarUpdateDTO dto, @MappingTarget CustomerHoliday customerHoliday);
}
