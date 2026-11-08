package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.response.GraceRegistrationItemDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationDistributionDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationSummaryDTO;
import com.travel_system.backend_app.model.dtos.response.StalledRegistrationItemDTO;
import com.travel_system.backend_app.service.RegistrationReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/registrations/reports")
public class RegistrationReportController {

    private final RegistrationReportService registrationReportService;

    public RegistrationReportController(RegistrationReportService registrationReportService) {
        this.registrationReportService = registrationReportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<RegistrationSummaryDTO> getRegistrationSummary() {
        return ResponseEntity.ok().body(registrationReportService.getRegistrationSummary());
    }

    @GetMapping(value = {"/distribution", "/distribution/{termId}"})
    public ResponseEntity<RegistrationDistributionDTO> getDistributionByTerm(@PathVariable(required = false) UUID termId) {
        return ResponseEntity.ok().body(registrationReportService.getDistributionByTerm(termId));
    }

    @GetMapping("/grace")
    public ResponseEntity<Page<GraceRegistrationItemDTO>> listInGrace(@RequestParam(defaultValue = "false") boolean onlyBlockingSoon, @PageableDefault(value = 15) Pageable pageable) {
        return ResponseEntity.ok().body(registrationReportService.listInGrace(onlyBlockingSoon, pageable));
    }

    @GetMapping("/stalled")
    public ResponseEntity<Page<StalledRegistrationItemDTO>> listStalled(@RequestParam(defaultValue = "7") int minDays, @PageableDefault(value = 15) Pageable pageable) {
        return ResponseEntity.ok().body(registrationReportService.listStalled(minDays, pageable));
    }
}
