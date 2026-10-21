package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.response.SensitiveDataAccessLogResponseDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.SensitiveDataAccessLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sensitive-access-logs")
@RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
public class SensitivesAccessLogController {

    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    public SensitivesAccessLogController(SensitiveDataAccessLogService sensitiveDataAccessLogService) {
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<SensitiveDataAccessLogResponseDTO>> getAllSensitivesAccessLog(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID actorAccountId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(value = 15) @SortDefault(direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(sensitiveDataAccessLogService.getAllSensitivesAccessLog(ownerId, actorAccountId, from, to, pageable));
    }
}
