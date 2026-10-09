package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.exceptions.CityNotFoundException;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.interfaces.mappers.CustomerRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerResponseMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.request.ChangeCustomerPlanPayload;
import com.travel_system.backend_app.model.dtos.request.CustomerOperationDataRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerOperationDataResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationAuthorizationResult;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CityRepository cityRepository;
    private final UserAccountRepository userAccountRepository;

    private final SetupAuthenticationService setupAuthenticationService;
    private final SensitiveOperationService sensitiveOperationService;

    private final CustomerResponseMapper customerResponseMapper;
    private final CustomerRequestMapper customerRequestMapper;

    public CustomerService(CustomerRepository customerRepository, CityRepository cityRepository, UserAccountRepository userAccountRepository, SetupAuthenticationService setupAuthenticationService, SensitiveOperationService sensitiveOperationService, CustomerResponseMapper customerResponseMapper, CustomerRequestMapper customerRequestMapper) {
        this.customerRepository = customerRepository;
        this.cityRepository = cityRepository;
        this.userAccountRepository = userAccountRepository;
        this.setupAuthenticationService = setupAuthenticationService;
        this.sensitiveOperationService = sensitiveOperationService;
        this.customerResponseMapper = customerResponseMapper;
        this.customerRequestMapper = customerRequestMapper;
    }

    @Transactional(readOnly = true)
    public Page<CustomerOperationDataResponseDTO> getAllCustomers(String name, String cnpj, String contactEmail, String contactTelephone, GeneralStatus status, ClientSector clientSector, CustomerPlan plan, Pageable pageable) {

        return customerRepository.findAllByOptionalFilters(name, cnpj, contactEmail, contactTelephone, status, clientSector, plan, pageable)
                .map(customerResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public CustomerOperationDataResponseDTO findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customerResponseMapper.toDTO(customer);
    }

    @Transactional(readOnly = true)
    public CustomerOperationDataResponseDTO findBySlug(String slug) {
        Customer customer = customerRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customerResponseMapper.toDTO(customer);
    }

    @Transactional
    public CustomerOperationDataResponseDTO createCustomer(CustomerOperationDataRequestDTO dto) {
        /* a city precisa ser cadastrada antes
         * o customer sempre começa com o plano LITE, caso seja um plano maior alterar em endpoint próprio
         * esse create será para criar o customer com dados operacionais básicos usados pela equipe Ziggo. Dados operacionais do próprio Customer
         * são cadastrados em endpoint específico pelo próprio Administrador do customer.
        */

        City city = cityRepository.findById(dto.cityId())
                .orElseThrow(() -> new CityNotFoundException("City não encontrada"));

        // valida existência de dados duplicados a nível global
        if (customerRepository.existsByCnpjIgnoringTenant(dto.cnpj())) {
            throw new DuplicateResourceException("Customer com esse CNPJ já existe no sistema");
        }

        if (customerRepository.existsByContactEmailIgnoringTenant(dto.contactEmail())) {
            throw new DuplicateResourceException("Customer com esse email de contato já existe no sistema");
        }

        if (customerRepository.existsByContactTelephoneIgnoringTenant(dto.contactTelephone())) {
            throw new DuplicateResourceException("Customer com esse telefone de contato já existe no sistema");
        }

        Customer customer = customerRequestMapper.toEntity(dto);

        customer.setCity(city);
        city.addCustomer(customer);

        Customer savedCustomer = customerRepository.save(customer);

        return customerResponseMapper.toDTO(savedCustomer);
    }

    @Transactional
    public CustomerOperationDataResponseDTO updateCustomer(UUID id, CustomerUpdateDTO customerUpdateDTO) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        if (customer.getStatus() == GeneralStatus.INACTIVE) throw new InactiveAccountModificationException("Customer não está ativo");

        customerRequestMapper.updateEntityFromDTO(customerUpdateDTO, customer);

        return customerResponseMapper.toDTO(customerRepository.save(customer));
    }

    // mudar plano (adc mais limitações aos planos)
    @Transactional
    public SensitiveOperationResponseDTO updateCustomerPlan(ChangeCustomerPlanPayload dto) {
        if (!customerRepository.existsByCnpj(dto.cnpj())) {
            throw new CustomerNotFoundException("Customer não encontrado");
        }

        // operação sensível
        String email = getAuthenticatedUserEmail().toLowerCase(Locale.ROOT).trim();

        // chama autenticação novamente
        setupAuthenticationService.consumeSensitiveOperationPermission(email, SensitiveOperationType.CHANGE_CUSTOMER_PLAN);

        SensitiveOperationAuthorizationResult sensitiveOperationAuthorization = sensitiveOperationService.createSensitiveOperationAuthorization(email, dto);

        SensitiveOperation sensitiveOperation = sensitiveOperationAuthorization.sensitiveOperation();

        return new SensitiveOperationResponseDTO(
                sensitiveOperation.getId(),
                sensitiveOperation.getSensitiveOperationStatus(),
                "Verifique o e-mail de aprovação para concluir a operação",
                sensitiveOperation.getExpiresAt()
        );
    }

    @Transactional
    public void updateCustomerStatus(UUID id, UpdateStatusDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        if (customer.getStatus() == dto.status()) throw new DuplicateResourceException("Customer já possui o status: " + dto.status());

        customer.setStatus(dto.status());

        customerRepository.save(customer);
    }
}

/*
* service operacional dos customers
* apenas para administradores do ziggo
* */