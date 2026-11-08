package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.dtos.request.CustomerTermResponseDTO;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.List;
import java.util.UUID;

public record RegistrationDistributionDTO(
        TermDTO term,
        TotalsDTO totals,
        List<ByInstitutionsDTO> byInstitutions,
        List<ByShiftsDTO> byShifts,
        List<ByInstitutionAndShiftDTO> byInstitutionAndShift,
        List<ByRouteDTO> byRoute

) {
    public record TermDTO(
            CustomerTermResponseDTO termResponse,
            boolean isCurrent
    ) {}

    public record TotalsDTO(
           long studentsCount,
           long driversCount,
           long studentsWithoutRoutesCount,
           long studentsWithRoutesCount
    ) {}

    public record ByInstitutionsDTO(
            UUID institutionId,
            String institutionName,
            InstitutionType institutionType,
            List<CourseDistributionDTO> courses,
            long totalCourses
    ) {}

    public record CourseDistributionDTO(
            UUID courseId,
            String courseName,
            long total
    ) {}

    public record ByShiftsDTO(
            Shift shifts,
            long totalShifts
    ){}

    // totais de instituições por turno
    public record ByInstitutionAndShiftDTO(
            UUID institutionId,
            String institutionName,
            List<ByShiftsDTO> shifts
    ){}

    public record ByRouteDTO(
            UUID standardRouteId,
            String standardRouteName,
            long total,
            List<RouteScheduleDTO> schedules
    ) {}

    public record RouteScheduleDTO(
        TravelPeriod travelPeriod,
        TravelDirection travelDirection,
        long total
    ) {}
}
