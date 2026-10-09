package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CustomerInfoResponseDTO(
        String name,
        String logoUrl,
        String cityName,
        String contactEmail,
        String contactTelephone,
        AddressResponseDTO addressResponse,
        Set<Shift> shifts,
        Set<DayOfWeek> operatingDays,
        String timeZone,

        /*
        * dados de rotas ativas
        * */
        List<StandardRouteDTO> routes,

        /*
        * dados de calendário
        * */
        OperatingStatusDTO today,
        TermDTO currentTerm,
        List<HolidayDTO> upcomingHolidays
) {

    // DTOs locais para ROTA
    public record StandardRouteDTO(
            String name,
            String description,
            Set<TravelPeriod> periods,
            List<RouteStopDTO> stops
    ) {}

    public record RouteStopDTO(
            String name,
            String description,
            Double latitude,
            Double longitude,
            TravelDirection direction,
            int sequence,
            boolean optional
    ) {}

    // DTOs locais para CALENDÁRIO
    public record OperatingStatusDTO(LocalDate date, boolean operating, String reason) {}

    public record TermDTO(String name, LocalDate startTerm, LocalDate endTerm) {}

    public record HolidayDTO(LocalDate date, String description, Set<Shift> shifts) {}

}
