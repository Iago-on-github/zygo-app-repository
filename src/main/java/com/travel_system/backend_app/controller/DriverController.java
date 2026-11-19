package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.dtos.request.CnhNumberSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CpfSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverCnhExpirationDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/drivers")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    // requisições do admin para drivers

    @RequireAccess(AccessScope.UNRESTRICTED)
    @GetMapping("/all")
    public ResponseEntity<Page<DriverResponseDTO>> getAllDrivers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) String areaOfActivity,
            @RequestParam(required = false) Set<Shift> shifts,
            @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok(driverService.getAllDrivers(email, name, lastName, neighborhood, areaOfActivity, shifts, pageable));
    }

    @RequireAccess(AccessScope.UNRESTRICTED)
    @GetMapping("/{driverId}")
    public ResponseEntity<DriverResponseDTO> getDriverById(@PathVariable UUID driverId) {
        return ResponseEntity.ok(driverService.getDriverById(driverId));
    }

    @RequireAccess(AccessScope.UNRESTRICTED)
    @PostMapping("/search-by-cpf")
    public ResponseEntity<DriverResponseDTO> getDriverByCpf(@Valid @RequestBody CpfSearchRequestDTO dto) {
        return ResponseEntity.ok(driverService.getDriverByCpf(dto));
    }

    @RequireAccess(AccessScope.UNRESTRICTED)
    @PostMapping("/search-by-cnh")
    public ResponseEntity<DriverResponseDTO> getDriverByCnhNumber(@Valid @RequestBody CnhNumberSearchRequestDTO dto) {
        return ResponseEntity.ok(driverService.getDriverByCnhNumber(dto));
    }

    @RequireAccess(AccessScope.UNRESTRICTED)
    @GetMapping("/cnh-expiring")
    public ResponseEntity<List<DriverCnhExpirationDTO>> getDriversWithCnhExpiringSoon(@RequestParam Set<CnhCategory> cnhCategories, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate thresholdDate) {

        // sem categorias informadas: considera todas
        Set<CnhCategory> categories = (cnhCategories == null || cnhCategories.isEmpty()) ? EnumSet.allOf(CnhCategory.class) : cnhCategories;

        return ResponseEntity.ok(driverService.getDriversWithCnhExpiringSoon(categories, thresholdDate));
    }

    @RequireAccess(AccessScope.UNRESTRICTED)
    @GetMapping("/status")
    public ResponseEntity<Page<DriverResponseDTO>> getDriversByStatus(@RequestParam(required = false) GeneralStatus status, @PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(driverService.getDriversByStatus(status,pageable));
    }

    // requisições próprias do driver

    @RequireAccess(AccessScope.REGULARIZATION)
    @GetMapping("/me")
    public ResponseEntity<DriverResponseDTO> getCurrentDriver() {
        DriverResponseDTO loggedDriver = driverService.getCurrentDriver();

        return ResponseEntity.ok().body(loggedDriver);
    }

    @RequireAccess(AccessScope.DOCUMENTS)
    @PatchMapping("/me")
    public ResponseEntity<DriverResponseDTO> updateCurrentDriver(@Valid @RequestBody DriverUpdateDTO driverUpdateDTO) {
        DriverResponseDTO loggedDriver = driverService.updateCurrentDriver(driverUpdateDTO);

        return ResponseEntity.ok().body(loggedDriver);
    }

    @RequireAccess(AccessScope.REGULARIZATION)
    @PatchMapping("/update/status")
    public ResponseEntity<Void> updateDriver(@Valid @RequestBody UpdateStatusDTO entityStatusDTO) {
        driverService.updateDriver(entityStatusDTO);

        return ResponseEntity.noContent().build();
    }

}
