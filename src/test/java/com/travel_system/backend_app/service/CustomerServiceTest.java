package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFoundException;
import com.travel_system.backend_app.exceptions.InactiveAccountModificationException;
import com.travel_system.backend_app.interfaces.mappers.CustomerRequestMapper;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @InjectMocks
    private CustomerService customerService;

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CityRepository cityRepository;
    @Mock
    private CustomerRequestMapper customerRequestMapper;

    Customer customer;
    CustomerRequestDTO customerRequestDTO;
    CustomerUpdateDTO customerUpdateDTO;

/*    @BeforeEach
    void setUp() {
        City city = new City();
        city.setId(UUID.randomUUID());

        customer = new Customer(UUID.randomUUID(), "Prefeitura X", "prefeitura-x", "093203/42-33", true, city, ClientSector.PUBLIC_CLIENT, null, Instant.now(), null);

        customerRequestDTO = new CustomerRequestDTO("Prefeitura Y", "prefeitura-y", "093203/42-33", city.getId(), Set.of(UUID.randomUUID()), ClientSector.PUBLIC_CLIENT, null);

        customerUpdateDTO = new CustomerUpdateDTO("NewCustomerName", "NewProfilePicture");
    }*/

    @Nested
    class getAllCustomers {

        @Test
        @DisplayName("Deve retornar a listagem de paginação com todos os customers com sucesso")
        void shouldReturnPageListWithAllCustomers() {
            List<Customer> customerList = List.of(customer);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Customer> customerPage = new PageImpl<>(customerList, pageable, customerList.size());

            when(customerRepository.findAll(pageable)).thenReturn(customerPage);

            Page<CustomerResponseDTO> result = customerService.getAllCustomers();

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());

            assertEquals(customer.getName(), result.getContent().getFirst().name());
            
            verify(customerRepository, times(1)).findAll(pageable);
        }

        @Test
        @DisplayName("Deve retornar a estrutura Page vazia quando não há customers")
        void shouldReturnEmptyPageStructureWhenHasNoCustomers() {
            Pageable pageable = PageRequest.of(0, 10);

            when(customerRepository.findAll(pageable)).thenReturn(Page.empty());

            Page<CustomerResponseDTO> result = customerService.getAllCustomers();

            assertNotNull(result);

            verify(customerRepository, times(1)).findAll(pageable);
        }
    }

    @Nested
    class findCustomerById {

        @Test
        @DisplayName("Deve retornar o customer pelo ID com sucess")
        void shouldReturnCustomerByIdWithSuccess() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            CustomerResponseDTO result = customerService.findCustomerById(customer.getId());

            assertNotNull(result);

            assertEquals(result.id(), customer.getId());
        }

        @Test
        @DisplayName("Deve lanaçar exception quando o Customer não for encontrado pelo ID")
        void throwExceptionWhenCustomerNotFoundById() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> customerService.findCustomerById(customer.getId()));
        }
    }

    @Nested
    class findCustomerBySlug {

        @Test
        @DisplayName("Deve retornar o customer pelo SLUG com sucess")
        void shouldReturnCustomerBySlugWithSuccess() {
            when(customerRepository.findBySlug(customer.getSlug())).thenReturn(Optional.of(customer));

            CustomerResponseDTO result = customerService.findCustomerBySlug(customer.getSlug());

            assertNotNull(result);

            assertEquals(result.id(), customer.getId());
            assertEquals(result.slug(), customer.getSlug());
        }

        @Test
        @DisplayName("Deve lançar exception quando o customer não for encontrado pelo SLUG")
        void throwExceptionWhenCustomerNotFoundBySlug() {
            when(customerRepository.findBySlug(customer.getSlug())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> customerService.findCustomerBySlug(customer.getSlug()));
        }
    }

    @Nested
    class findAllByActive {

        @Test
        @DisplayName("Deve retornar todos os customers com base no status fornecido")
        void shouldReturnAllCustomersByStatus() {
            when(customerRepository.findAllByActive(true)).thenReturn(List.of(customer));

            List<CustomerResponseDTO> result = customerService.findAllByActive(true);

            assertEquals(1, result.size());
            assertEquals(customer.getId(), result.getFirst().id());
            
            assertTrue(result.getFirst().active());
        }

        @Test
        @DisplayName("Deve retornar todos os customers ativos quando o parâmetro não for fornecido, tratando como ACTIVE por padrão")
        void shouldReturnAllActiveCustomersWhenParameterIsNull() {
            when(customerRepository.findAllByActive(true)).thenReturn(List.of(customer));

            List<CustomerResponseDTO> result = customerService.findAllByActive(null);

            assertEquals(1, result.size());
            assertEquals(customer.getId(), result.getFirst().id());

            assertTrue(result.getFirst().active());
        }
    }

/*    @Nested
    class createCustomer {

        @Test
        @DisplayName("Deve criar um novo customer com sucesso")
        void shouldCreateNewCustomerWithSuccess() {
            when(cityRepository.findById(customer.getCity().getId())).thenReturn(Optional.of(new City()));
            when(customerRepository.findByCnpj(customer.getCnpj())).thenReturn(Optional.empty());

            CustomerResponseDTO result = customerService.createCustomer(customerRequestDTO);

            assertNotNull(result);

            assertTrue(result.active());
            assertNotNull(result.createdAt());

            verify(cityRepository, times(1)).findById(any());
            verify(customerRepository, times(1)).findByCnpj(any());
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando os campos obrigatórios forem null")
        @MethodSource("nullRequireFieldsProvider")
        void throwExceptionWhenRequireFieldsAreNull(CustomerRequestDTO customerRequestDTO) {
            assertThrows(EmptyMandatoryFieldsFoundException.class, () -> customerService.createCustomer(customerRequestDTO));
            verify(cityRepository, never()).findById(any());
            verify(customerRepository, never()).findByCnpj(any());
            verify(customerRepository, never()).save(any());

        }

        public static Stream<Arguments> nullRequireFieldsProvider() {
            return Stream.of(
                    Arguments.of(new CustomerRequestDTO(null, "prefeitura-y", "093203/42-33", UUID.randomUUID(), Set.of(UUID.randomUUID()), ClientSector.PUBLIC_CLIENT, null)),
                    Arguments.of(new CustomerRequestDTO("Prefeitura Y", null, "093203/42-33", UUID.randomUUID(), Set.of(UUID.randomUUID()), ClientSector.PUBLIC_CLIENT, null)),
                    Arguments.of(new CustomerRequestDTO("Prefeitura Y", "prefeitura-y", "093203/42-33", null, Set.of(UUID.randomUUID()), ClientSector.PUBLIC_CLIENT, null)),
                    Arguments.of(new CustomerRequestDTO("Prefeitura Y", "prefeitura-y", "093203/42-33", UUID.randomUUID(), Set.of(UUID.randomUUID()), null, null)),
                    Arguments.of(new CustomerRequestDTO("Prefeitura Y", "prefeitura-y", "093203/42-33", UUID.randomUUID(), null, ClientSector.PUBLIC_CLIENT, null))
            );
        }

        @Test
        @DisplayName("Deve lançar exception quando a city não for encontrada durante a criação do customer")
        void throwExceptionWhenCityNotFound() {
            when(cityRepository.findById(customer.getCity().getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> customerService.createCustomer(customerRequestDTO));

            verify(cityRepository, times(1)).findById(any());

            verify(customerRepository, never()).findByCnpj(any());
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exception quando o CNPJ já existir no banco")
        void throwExceptionWhenCpnjAlreadyExists() {
            when(cityRepository.findById(customer.getCity().getId())).thenReturn(Optional.of(new City()));
            when(customerRepository.findByCnpj(customer.getCnpj())).thenReturn(Optional.of(customer));

            assertThrows(DuplicateResourceException.class, () -> customerService.createCustomer(customerRequestDTO));

            verify(cityRepository, times(1)).findById(any());
            verify(customerRepository, times(1)).findByCnpj(any());

            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    class updateCustomer {

        @Test
        @DisplayName("Deve realizar a atualizaçao do Customer com sucesso")
        void shouldUpdateCustomerWithSuccess() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            CustomerResponseDTO result = customerService.updateCustomer(customer.getId(), customerUpdateDTO);

            assertNotNull(result);

            ArgumentCaptor<Customer> customerArgCaptor = ArgumentCaptor.forClass(Customer.class);

            verify(customerRequestMapper, times(1)).customerMapper(customerUpdateDTO, customer);
            verify(customerRepository, times(1)).save(customerArgCaptor.capture());

            Customer storageCustomer = customerArgCaptor.getValue();
            assertNotNull(storageCustomer.getUpdatedAt());
        }

        @Test
        @DisplayName("Deve lançar exception quando o customer não for encontrado")
        void throwExceptionWhenCustomerNotFound() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> customerService.updateCustomer(customer.getId(), customerUpdateDTO));

            verify(customerRequestMapper, never()).customerMapper(any(), any());
            verify(customerRepository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("Deve lançar exception quando o customer estiver inativo")
        void throwExceptionWhenCustomerIsInactive() {
            customer.setActive(false);

            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThrows(InactiveAccountModificationException.class, () -> customerService.updateCustomer(customer.getId(), customerUpdateDTO));

            verify(customerRepository, times(1)).findById(any());

            verify(customerRequestMapper, never()).customerMapper(any(), any());
            verify(customerRepository, never()).save(any());
        }
    }*/
    
    @Nested
    class updateCustomerActive {

        @Test
        @DisplayName("Deve desativar o customer mudando o seu status para 'false' com sucesso")
        void shouldDisableCustomerWithSuccess() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            customerService.updateCustomerActive(customer.getId(), false);

            verify(customerRepository, times(1)).findById(any());
        }

        @Test
        @DisplayName("Decve ativar o customer mudando o seu status para 'true' com sucesso")
        void shouldActivateCustomerWithSuccess() {
            customer.setActive(false);

            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            customerService.updateCustomerActive(customer.getId(), true);

            verify(customerRepository, times(1)).findById(any());
        }

        @Test
        void throwExceptionWhenCustomerNotFound() {
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> customerService.updateCustomerActive(customer.getId(), true));

            verifyNoMoreInteractions(customerRepository);
        }

        @Test
        @DisplayName("Deve lançar exception quando o customer já estiver inativo no sistema")
        void throwExceptionWhenCustomerAlreadyInactive() {
            customer.setActive(false);

            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThrows(InactiveAccountModificationException.class, () -> customerService.updateCustomerActive(customer.getId(), false));

            verifyNoMoreInteractions(customerRepository);
        }
    }
}