package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFoundException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.interfaces.mappers.CustomerRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerResponseMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CityRepository cityRepository;
    private final UserAccountRepository userAccountRepository;

    private final CustomerResponseMapper customerResponseMapper;
    private final CustomerRequestMapper customerRequestMapper;

    public CustomerService(CustomerRepository customerRepository, CityRepository cityRepository, UserAccountRepository userAccountRepository, CustomerResponseMapper customerResponseMapper, CustomerRequestMapper customerRequestMapper) {
        this.customerRepository = customerRepository;
        this.cityRepository = cityRepository;
        this.userAccountRepository = userAccountRepository;
        this.customerResponseMapper = customerResponseMapper;
        this.customerRequestMapper = customerRequestMapper;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponseDTO> getAllCustomers() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Customer> customers = customerRepository.findAll(pageable);

        return customers.map(customerResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        return customerResponseMapper.toDTO(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO findCustomerBySlug(String slug) {
        Customer customer = customerRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o slug '" + slug + "' não encontrado"));

        return customerResponseMapper.toDTO(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponseDTO> findByStatus(GeneralStatus status) {
        if (status == null) status = GeneralStatus.ACTIVE;

        List<Customer> customersByStatus = customerRepository.findAllByStatus(status);

        return customersByStatus.stream().map(customerResponseMapper::toDTO).toList();
    }

    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {
        // valida preenchimento de campos obrigatórios
        validateRequireFields(customerRequestDTO);

        City city = cityRepository.findById(customerRequestDTO.cityId()).orElseThrow(() -> new EntityNotFoundException("City não encontrada."));

        // valida existência de CNPJ
        if (customerRepository.existsByCnpj(customerRequestDTO.cnpj())) {
            throw new DuplicateResourceException("Customer com o CNPJ " + customerRequestDTO.cnpj() + " já existe na base de dados.");
        }

        Customer customer = customerRequestMapper.toEntity(customerRequestDTO);
        customer.setCity(city);

        Customer savedCustomer = customerRepository.save(customer);

        return customerResponseMapper.toDTO(savedCustomer);
    }

    @Transactional
    public CustomerResponseDTO updateCustomer(UUID id, CustomerUpdateDTO customerUpdateDTO) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        if (customer.getStatus() == GeneralStatus.INACTIVE) throw new InactiveAccountModificationException("Customer não está ativo.");

        customerRequestMapper.updateEntityFromDTO(customerUpdateDTO, customer);

        return customerResponseMapper.toDTO(customerRepository.save(customer));
    }

    @Transactional
    public void updateCustomerActive(UUID id, UpdateEntityStatusDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        if (customer.getStatus() == dto.status()) throw new DuplicateResourceException("Customer já possui o status: " + dto.status());

        customer.setStatus(dto.status());

        customerRepository.save(customer);
    }

    private void validateRequireFields(CustomerRequestDTO customerRequestDTO) {
        if (customerRequestDTO.name() == null || customerRequestDTO.slug() == null || customerRequestDTO.cityId() == null || customerRequestDTO.clientSector() == null) {
            throw new EmptyMandatoryFieldsFoundException("Preencha todos os campos obrigatórios");
        }
    }
}
