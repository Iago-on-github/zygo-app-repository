package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.VehicleResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.VehicleType;
import com.travel_system.backend_app.service.VehicleService;
import com.travel_system.backend_app.service.profilePicture.VehicleProfilePictureService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/vehicle")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleProfilePictureService vehicleProfilePictureService;

    public VehicleController(VehicleService vehicleService, VehicleProfilePictureService vehicleProfilePictureService) {
        this.vehicleService = vehicleService;
        this.vehicleProfilePictureService = vehicleProfilePictureService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<VehicleResponseDTO>> getAllVehicles(
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) String vehicleNumber,
            @RequestParam(required = false) String numberPlate,
            @RequestParam(required = false) GeneralStatus status,
            @RequestParam(required = false) String color,
            @PageableDefault(size = 15) Pageable pageable) {
        return ResponseEntity.ok().body(vehicleService.getAllVehicles(vehicleType, vehicleNumber, numberPlate, status, color, pageable));
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponseDTO> getVehicleById(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok().body(vehicleService.getVehicleById(vehicleId));
    }

    @PostMapping("/new")
    public ResponseEntity<VehicleResponseDTO> createVehicle(@Valid @RequestBody VehicleRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        VehicleResponseDTO vehicle = vehicleService.createVehicle(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(vehicle.id()).toUri();

        return ResponseEntity.created(uri).body(vehicle);
    }

    @PatchMapping("/update/{vehicleId}")
    public ResponseEntity<VehicleResponseDTO> updateVehicle(@PathVariable UUID vehicleId, @Valid @RequestBody VehicleUpdateDTO dto) {
        return ResponseEntity.ok().body(vehicleService.updateVehicle(vehicleId, dto));
    }

    @PatchMapping("/status/{vehicleId}")
    public ResponseEntity<Void> updateVehicleStatus(@PathVariable UUID vehicleId, @Valid @RequestBody UpdateStatusDTO dto) {
        vehicleService.updateVehicleStatus(vehicleId, dto);

        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{vehicleId}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateVehiclePicture(@PathVariable UUID vehicleId, @RequestParam("file") MultipartFile file) throws IOException {
        vehicleProfilePictureService.updateVehiclePicture(vehicleId, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{vehicleId}/picture")
    public ResponseEntity<Void> deleteVehiclePicture(@PathVariable UUID vehicleId) {
        vehicleProfilePictureService.deleteVehiclePicture(vehicleId);
        return ResponseEntity.noContent().build();
    }

}
