package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CityRequestDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CityResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.CityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/cities")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public ResponseEntity<List<CityResponseDTO>> getAllCities() {
        return ResponseEntity.ok().body(cityService.getAllCities());
    }

    @GetMapping("/{cityId}")
    public ResponseEntity<CityResponseDTO> getCityById(@PathVariable UUID cityId) {
        return ResponseEntity.ok().body(cityService.getCityById(cityId));
    }

    @GetMapping("/status")
    public ResponseEntity<List<CityResponseDTO>> getCitiesByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(cityService.getCitiesByStatus(status));
    }

    @PostMapping
    public ResponseEntity<CityResponseDTO> createCity(@Valid @RequestBody CityRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        CityResponseDTO newCity = cityService.createCity(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newCity.id()).toUri();

        return ResponseEntity.created(uri).body(newCity);
    }

    @PatchMapping("/{cityId}/removeCustomer/{customerId}")
    public ResponseEntity<Void> removeCustomer(@PathVariable UUID cityId, @PathVariable UUID customerId) {
        cityService.removeCustomer(cityId, customerId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{cityId}/status")
    public ResponseEntity<Void> updateCityStatus(@PathVariable UUID cityId, @Valid @RequestBody UpdateEntityStatusDTO dto) {
        cityService.updateCityStatus(cityId, dto);

        return ResponseEntity.noContent().build();
    }

}
