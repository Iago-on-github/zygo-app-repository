package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.interfaces.mappers.CustomerMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository, CityRepository cityRepository, UserRepository userRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
        this.customerMapper = customerMapper;
    }

    public Page<CustomerResponseDTO> getAllCustomers() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Customer> customers = customerRepository.findAll(pageable);

        return customers.map(this::customerResponseDtoMapper);
    }

    public CustomerResponseDTO findCustomerById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        return customerResponseDtoMapper(customer);
    }

    public CustomerResponseDTO findCustomerBySlug(String slug) {
        Customer customer = customerRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o slug '" + slug + "' não encontrado"));

        return customerResponseDtoMapper(customer);
    }

    public List<CustomerResponseDTO> findAllByActive(Boolean active) {
        if (active == null) active = true; // se não for fornecido, trata como true

        List<Customer> customersByStatus = customerRepository.findAllByActive(active);

        return customersByStatus.stream().map(this::customerResponseDtoMapper).toList();
    }

    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {
        validateRequireFields(customerRequestDTO); // valida preenchimento de campos obrigatórios

        City city = cityRepository.findById(customerRequestDTO.cityId()).orElseThrow(() -> new EntityNotFoundException("City não encontrada."));

        Set<UserModel> users = new HashSet<>();
        if (customerRequestDTO.userIds() != null && !customerRequestDTO.userIds().isEmpty()) {
            users = new HashSet<>(userRepository.findAllById(customerRequestDTO.userIds()));

            // verifica se houve resultado retornado no banco
            if (users.size() != customerRequestDTO.userIds().size()) {
                throw new EntityNotFoundException("Nenhum usuário encontrado");
            }
        }

        boolean isCnpjAlreadyExists = customerRepository.findByCnpj(customerRequestDTO.cnpj()).isPresent();

        if (isCnpjAlreadyExists) throw new DuplicateResourceException("Customer com o CNPJ " + customerRequestDTO.cnpj() + " já existe na base de dados.");

        Customer customer = customerMapper(customerRequestDTO, users);
        customer.setCity(city);
        customer.setUsers(users);

        customerRepository.save(customer);

        return customerResponseDtoMapper(customer);
    }

    @Transactional
    public CustomerResponseDTO updateCustomer(UUID id, CustomerUpdateDTO customerUpdateDTO) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        if (!customer.isActive()) throw new InactiveAccountModificationException("Customer não está ativo.");

        customerMapper.customerMapper(customerUpdateDTO, customer);
        customer.setUpdatedAt(Instant.now());

        customerRepository.save(customer);

        return customerResponseDtoMapper(customer);
    }

    @Transactional
    public void updateCustomerActive(UUID id, boolean isEnabled) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer com o id '" + id + "' não encontrado"));

        if (customer.isActive() == isEnabled) throw new InactiveAccountModificationException("Customer já inativo no sistema");

        customer.setActive(isEnabled);
    }

    private void validateRequireFields(CustomerRequestDTO customerRequestDTO) {
        if (customerRequestDTO.name() == null || customerRequestDTO.slug() == null || customerRequestDTO.cityId() == null || customerRequestDTO.userIds() == null
        || customerRequestDTO.clientSector() == null) {
            throw new EmptyMandatoryFieldsFound("Preencha todos os campos obrigatórios");
        }
    }

    private CustomerResponseDTO customerResponseDtoMapper(Customer customer) {
        return new CustomerResponseDTO(
                customer.getId(),
                customer.getName(),
                customer.getSlug(),
                customer.getCnpj(),
                customer.isActive(),
                customer.getCity(),
                customer.getClientSector(),
                customer.getProfilePicture(),
                customer.getCreatedAt()
        );
    }

    private Customer customerMapper(CustomerRequestDTO customerRequestDTO, Set<UserModel> users) {
        Customer customer = new Customer();

        // relaciona customer com UserModel
        for (UserModel user : users) {
            user.setCustomer(customer);
        }

        customer.setName(customerRequestDTO.name());
        customer.setSlug(customerRequestDTO.slug());
        customer.setCnpj(customerRequestDTO.cnpj());
        customer.setActive(true);
        customer.setClientSector(customerRequestDTO.clientSector());
        customer.setProfilePicture(customerRequestDTO.profilePicture());
        customer.setCreatedAt(Instant.now());

        return customer;
    }
}
