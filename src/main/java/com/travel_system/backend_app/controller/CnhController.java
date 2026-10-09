package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CnhNumberSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CnhRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CnhUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.CnhResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import com.travel_system.backend_app.service.CnhService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/cnh")
public class CnhController {

    private final CnhService cnhService;

    public CnhController(CnhService cnhService) {
        this.cnhService = cnhService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<CnhResponseDTO>> getAllCnh(
            @RequestParam(required = false) String cnhNumber,
            @RequestParam(required = false) Set<CnhCategory> cnhCategories,
            @RequestParam(required = false) LocalDate cnhExpirationDate,
            @RequestParam(required = false) LocalDate cnhFirstIssueDate,
            @PageableDefault(size = 15) @SortDefault(sort = "cnhNumber", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok().body(cnhService.getAllCnh(cnhNumber, cnhCategories, cnhExpirationDate, cnhFirstIssueDate, pageable));
    }

    @GetMapping("/{cnhId}")
    public ResponseEntity<CnhResponseDTO> getCnhById(@PathVariable UUID cnhId) {
        return ResponseEntity.ok().body(cnhService.getCnhById(cnhId));
    }

    @GetMapping("/number")
    public ResponseEntity<CnhResponseDTO> getCnhByNumber(@Valid @RequestBody CnhNumberSearchRequestDTO dto) {
        return ResponseEntity.ok().body(cnhService.getCnhByNumber(dto));
    }

    @PatchMapping("/update/{cnhId}")
    public ResponseEntity<CnhResponseDTO> updateCnh(@PathVariable UUID cnhId, @Valid @RequestBody CnhUpdateDTO dto) {
        CnhResponseDTO cnh = cnhService.updateCnh(cnhId, dto);

        return ResponseEntity.ok().body(cnh);
    }

}
