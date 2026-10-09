package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CustomerMismatchException;
import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.VehicleNotFoundException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.mappers.VehicleRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.VehicleResponseMapper;
import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.VehicleResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.VehicleType;
import com.travel_system.backend_app.repository.VehicleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    private final VehicleRequestMapper vehicleRequestMapper;
    private final VehicleResponseMapper vehicleResponseMapper;

    public VehicleService(VehicleRepository vehicleRepository, VehicleRequestMapper vehicleRequestMapper, VehicleResponseMapper vehicleResponseMapper) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleRequestMapper = vehicleRequestMapper;
        this.vehicleResponseMapper = vehicleResponseMapper;
    }

    /*
    * recupera todos os veículos com base no filtro opcional
    * */
    @Transactional(readOnly = true)
    public Page<VehicleResponseDTO> getAllVehicles(VehicleType vehicleType, String vehicleNumber, String numberPlate, GeneralStatus status, String color, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        // placa sem hífen/espaços e em maiúsculas
        String normalizedPlate = blankToNull(numberPlate);
        if (normalizedPlate != null) {
            normalizedPlate = normalizedPlate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        }

        String normalizedColor = blankToNull(color);
        if (normalizedColor != null) {
            normalizedColor = normalizedColor.toLowerCase(Locale.ROOT);
        }

        PageRequest pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return vehicleRepository.findAllByOptionalFilters(
                        customerId,
                        vehicleType,
                        blankToNull(vehicleNumber),
                        normalizedPlate,
                        status,
                        normalizedColor,
                        pageOnly)
                .map(vehicleResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public VehicleResponseDTO getVehicleById(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicleResponseMapper::toDTO)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado"));
    }

    @Transactional
    public VehicleResponseDTO createVehicle(VehicleRequestDTO dto) {
        // valida duplicação de dados únicos
        if (vehicleRepository.existsByVehicleNumber(dto.vehicleNumber())) {
            throw new DuplicateResourceException("Número do veículo já cadastrado no sistema");
        }

        if (vehicleRepository.existsByNumberPlate(dto.numberPlate())) {
            throw new DuplicateResourceException("Placa do veículo já cadastrada no sistema");
        }

        Vehicle vehicle = vehicleRequestMapper.toEntity(dto);

        return vehicleResponseMapper.toDTO(vehicleRepository.save(vehicle));

    }

    @Transactional
    public VehicleResponseDTO updateVehicle(UUID vehicleId, VehicleUpdateDTO dto) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado"));

        // valida mesmo customer
        UUID currentTenant = getCurrentTenant();
        if (!currentTenant.equals(vehicle.getCustomerId())) {
            throw new CustomerMismatchException("Você não tem permissão para alterar veículos de outros espaços");
        }

        vehicleRequestMapper.updateFromDTO(dto, vehicle);

        return vehicleResponseMapper.toDTO(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void updateVehicleStatus(UUID vehicleId, UpdateStatusDTO dto) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado"));

        // valida mesmo customer
        UUID currentTenant = getCurrentTenant();
        if (!currentTenant.equals(vehicle.getCustomerId())) {
            throw new CustomerMismatchException("Você não tem permissão para alterar veículos de outros espaços");
        }

        // verifica se o status que o user está tentanto mudar é o mesmo do atual
        if (vehicle.getStatus() == dto.status()) {
            throw new DuplicateResourceException("Vehicle já com o status + " + dto.status());
        }

        vehicle.setStatus(dto.status());

        vehicleRepository.save(vehicle);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
