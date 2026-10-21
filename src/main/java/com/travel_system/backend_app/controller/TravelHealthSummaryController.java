package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.response.StudentHealthSummaryDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.TravelHealthSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/travel-summary")
@RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
public class TravelHealthSummaryController {

    private final TravelHealthSummaryService travelHealthSummaryService;

    public TravelHealthSummaryController(TravelHealthSummaryService travelHealthSummaryService) {
        this.travelHealthSummaryService = travelHealthSummaryService;
    }

    @GetMapping("/health")
    public ResponseEntity<List<StudentHealthSummaryDTO>> getCurrentSummary() {
        return ResponseEntity.ok().body(travelHealthSummaryService.getCurrentSummary());
    }
}
