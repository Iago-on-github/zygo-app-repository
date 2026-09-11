package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.InactiveAccountException;
import com.travel_system.backend_app.interfaces.mappers.CityRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CityResponseMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.CityRequestDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CityResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CityService {

    private final CityRepository cityRepository;
    private final CustomerRepository customerRepository;

    private final CityRequestMapper cityRequestMapper;
    private final CityResponseMapper cityResponseMapper;

    public CityService(CityRepository cityRepository, CustomerRepository customerRepository, CityRequestMapper cityRequestMapper, CityResponseMapper cityResponseMapper) {
        this.cityRepository = cityRepository;
        this.customerRepository = customerRepository;
        this.cityRequestMapper = cityRequestMapper;
        this.cityResponseMapper = cityResponseMapper;
    }

    @Transactional(readOnly = true)
    public List<CityResponseDTO> getAllCities() {
        List<City> cities = cityRepository.findAll();

        return cities.stream().map(cityResponseMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public CityResponseDTO getCityById(UUID id) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("City não encontrada pelo id: " + id));

        return cityResponseMapper.toDTO(city);
    }

    @Transactional(readOnly = true)
    public List<CityResponseDTO> getCitiesByStatus(GeneralStatus status) {
        // por padrão, caso não mande status, seta como null
        if (status == null) {
            status = GeneralStatus.ACTIVE;
        }

        List<City> cities = cityRepository.findByStatus(status);

        return cities.stream().map(cityResponseMapper::toDTO).toList();
    }

    @Transactional
    public CityResponseDTO createCity(CityRequestDTO dto) {
        City city = cityRequestMapper.toEntity(dto);

        City savedEntity = cityRepository.save(city);

        return cityResponseMapper.toDTO(savedEntity);
    }

    @Transactional
    public CityResponseDTO addCustomer(UUID cityId, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer não encontrado pelo Id: " + customerId));

        if (customer.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Não é possível adicionar um Customer que está inativo");
        }

        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("City não encontrada pelo id: " + cityId));

        city.addCustomer(customer);

        return cityResponseMapper.toDTO(city);
    }

    @Transactional
    public void removeCustomer(UUID cityId, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer não encontrado pelo Id: " + customerId));

        if (customer.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Customer Inativo no sistema");
        }

        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("City não encontrada pelo id: " + cityId));

        if (customer.getCity() == null || !customer.getCity().getId().equals(cityId)) {
            throw new IllegalArgumentException("O Customer " + customerId + " não pertence à cidade " + cityId);
        }

       city.removeCustomer(customer);
    }

    @Transactional
    public void updateCityStatus(UUID cityId, UpdateEntityStatusDTO dto) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new EntityNotFoundException("City não encontrada pelo id: " + cityId));

        if (city.getStatus() == dto.status()) {
            throw new DuplicateResourceException("City já está com o status: " + dto.status());
        }

        city.setStatus(dto.status());

        cityRepository.save(city);
    }
}
