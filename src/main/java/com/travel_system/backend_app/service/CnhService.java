package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CnhNotFoundException;
import com.travel_system.backend_app.exceptions.CustomerMismatchException;
import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.mappers.CnhRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CnhResponseMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Cnh;
import com.travel_system.backend_app.model.dtos.request.CnhNumberSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CnhRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CnhUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CnhResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.CnhCategory;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CnhRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CnhService {

    private final CnhRepository cnhRepository;

    private final CnhRequestMapper cnhRequestMapper;
    private final CnhResponseMapper cnhResponseMapper;

    public CnhService(AdministratorRepository administratorRepository, CnhRepository cnhRepository, CnhRequestMapper cnhRequestMapper, CnhResponseMapper cnhResponseMapper) {
        this.cnhRepository = cnhRepository;
        this.cnhRequestMapper = cnhRequestMapper;
        this.cnhResponseMapper = cnhResponseMapper;
    }

    /*
    * recupera todas as cnhs com base nos filtros opcionais
    * */
    @Transactional(readOnly = true)
    public Page<CnhResponseDTO> getAllCnh(String cnhNumber, Set<CnhCategory> cnhCategories, LocalDate cnhExpirationDate, LocalDate cnhFirstIssueDate, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        // "123.456.789-01" e "12345678901" são o mesmo número
        String normalizedCnhNumber = blankToNull(cnhNumber);
        if (normalizedCnhNumber != null) {
            normalizedCnhNumber = normalizedCnhNumber.replaceAll("\\D", "");
        }

        boolean filterByCategories = cnhCategories != null && !cnhCategories.isEmpty();

        // sem filtro, a lista ainda precisa ser válida para o SQL (não pode ser vazia)
        Set<CnhCategory> categories = filterByCategories ? cnhCategories : EnumSet.allOf(CnhCategory.class);

        return cnhRepository.findAllByOptionalFilters(
                        customerId,
                        normalizedCnhNumber,
                        filterByCategories,
                        categories,
                        cnhExpirationDate,
                        cnhFirstIssueDate,
                        pageable)
                .map(cnhResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public CnhResponseDTO getCnhById(UUID cnhId) {
        return cnhRepository.findById(cnhId)
                .map(cnhResponseMapper::toDTO)
                .orElseThrow(() -> new CnhNotFoundException("Nenhuma cnh encontrada"));
    }

    @Transactional(readOnly = true)
    public CnhResponseDTO getCnhByNumber(CnhNumberSearchRequestDTO dto) {
        String normalizeCnhNumber = dto.cnhNumber().replaceAll("\\D", "");

        return cnhResponseMapper.toDTO(cnhRepository.findByCnhNumber(normalizeCnhNumber)
                .orElseThrow(() -> new CnhNotFoundException("Nenhuma cnh encontrada")));
    }

    // usa no endpoint de criar o driver
    @Transactional
    protected Cnh createCnh(CnhRequestDTO dto, UUID customerId) {

        if (cnhRepository.existsByCnhNumber(dto.cnhNumber())) {
            throw new DuplicateResourceException("Número de CNH já cadastrado no sistema");
        }

        Cnh cnh = cnhRequestMapper.toEntity(dto);

        cnh.assignCustomer(customerId);

        return cnhRepository.save(cnh);
    }

    @Transactional
    public CnhResponseDTO updateCnh(UUID cnhId, CnhUpdateDTO dto) {
        Cnh cnh = cnhRepository.findById(cnhId).orElseThrow(() -> new CnhNotFoundException("Nenhuma cnh encontrada"));

        if (dto.cnhNumber() != null && !dto.cnhNumber().isBlank()) {
            if (dto.cnhNumber().equals(cnh.getCnhNumber())) {
                if (cnhRepository.existsByCnhNumber(dto.cnhNumber())) {
                    throw new DuplicateResourceException("Número de CNH já cadastrado no sistema");
                }
            }
        }

        // valida mesmo customer
        UUID currentTenant = getCurrentTenant();
        if (currentTenant.equals(cnh.getCustomerId())) {
            throw new CustomerMismatchException("Você não tem permissão para alterar veículos de outros espaços");
        }

        if (!dto.cnhCategories().isEmpty()) {
            cnh.getCnhCategories().addAll(dto.cnhCategories());
        }

        cnhRequestMapper.updateFromDTO(dto, cnh);

        return cnhResponseMapper.toDTO(cnhRepository.save(cnh));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

}
