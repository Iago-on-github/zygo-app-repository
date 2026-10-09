package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.CustomerHolidayRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.CustomerTermRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerHolidayResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerTermResponseMapper;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.CustomerHoliday;
import com.travel_system.backend_app.model.CustomerTerm;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.CustomerHolidayResponseDTO;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.CustomerHolidayRepository;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.CustomerTermRepository;
import com.travel_system.backend_app.utils.DateTimeFormats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CustomerCalendarService {

    private final AdministratorRepository administratorRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTermRepository customerTermRepository;
    private final CustomerHolidayRepository customerHolidayRepository;


    private final CustomerHolidayRequestMapper customerHolidayRequestMapper;
    private final CustomerHolidayResponseMapper customerHolidayResponseMapper;

    private final CustomerTermRequestMapper customerTermRequestMapper;
    private final CustomerTermResponseMapper customerTermResponseMapper;

    public CustomerCalendarService(AdministratorRepository administratorRepository, CustomerRepository customerRepository, CustomerTermRepository customerTermRepository, CustomerHolidayRepository customerHolidayRepository, DateTimeFormats dateTimeFormats, CustomerHolidayRequestMapper customerHolidayRequestMapper, CustomerTermRequestMapper customerTermRequestMapper, CustomerHolidayResponseMapper customerHolidayResponseMapper, CustomerTermResponseMapper customerTermResponseMapper) {
        this.administratorRepository = administratorRepository;
        this.customerRepository = customerRepository;
        this.customerTermRepository = customerTermRepository;
        this.customerHolidayRepository = customerHolidayRepository;
        this.customerHolidayRequestMapper = customerHolidayRequestMapper;
        this.customerTermRequestMapper = customerTermRequestMapper;
        this.customerHolidayResponseMapper = customerHolidayResponseMapper;
        this.customerTermResponseMapper = customerTermResponseMapper;
    }

    /* FERIADOS */
    @Transactional(readOnly = true)
    public Page<CustomerHolidayResponseDTO> getAllCustomerHolidays(LocalDate dateFrom, LocalDate dateTo, Set<Shift> shifts, Pageable pageable) {

        // as validações de intervalo só se aplicam quando as duas datas são informadas
        if (dateFrom != null && dateTo != null) {
            if (dateFrom.isAfter(dateTo)) {
                throw new DomainValidationException("A data inicial deve ser anterior à data final");
            }
            if (dateFrom.plusYears(1).isBefore(dateTo)) {
                throw new DomainValidationException("O intervalo máximo de consulta é de 1 ano");
            }
        }

        boolean filterByShifts = shifts != null && !shifts.isEmpty();
        Set<Shift> shiftsFilter = filterByShifts ? shifts : EnumSet.allOf(Shift.class);

        return customerHolidayRepository.findAllByPeriod(dateFrom, dateTo, filterByShifts, shiftsFilter, pageable)
                .map(customerHolidayResponseMapper::toDTO);
    }

    @Transactional
    public CustomerHolidayResponseDTO createHoliday(CustomerCalendarRequestDTO dto) {

        // verifica se já não existe um feriado já criado nessa data
        if (customerHolidayRepository.existsByDate(dto.holidayDate())) {
            throw new HasAlreadyHolidayDateException("Já existe uma feriado cadastrado nessa data");
        }

        CustomerHoliday customerHoliday = customerHolidayRequestMapper.toEntity(dto);

        Administrator admin = findAdmin();
        customerHoliday.setCreatedBy(admin.getId());

        CustomerHoliday savedCustomerHoliday = customerHolidayRepository.save(customerHoliday);

        return customerHolidayResponseMapper.toDTO(savedCustomerHoliday);
    }

    @Transactional
    public CustomerHolidayResponseDTO updateHoliday(UUID customerHolidayId, CustomerCalendarUpdateDTO dto) {
        CustomerHoliday customerHoliday = customerHolidayRepository.findById(customerHolidayId)
                .orElseThrow(() -> new CustomerHolidayNotFoundException("Feriado não encontrado"));

        customerHolidayRequestMapper.updateFromDTO(dto, customerHoliday);

        CustomerHoliday savedCustomerHoliday = customerHolidayRepository.save(customerHoliday);

        // lança notificação para os alunos (+ responsávies) e os motoristas daquele turno

        return customerHolidayResponseMapper.toDTO(savedCustomerHoliday);
    }

    @Transactional
    public void deleteHoliday(UUID customerHolidayId) {
        CustomerHoliday customerHoliday = customerHolidayRepository.findById(customerHolidayId)
                .orElseThrow(() -> new CustomerHolidayNotFoundException("Feriado não encontrado"));

        // lança notificação para os alunos (+ responsávies) e os motoristas daquele turno

        customerHolidayRepository.deleteById(customerHoliday.getId());
    }

    /*
     * PERÍODOS LETIVOS
     * */
    @Transactional(readOnly = true)
    public CustomerTermResponseDTO getCurrentTerm() {
        LocalDate today = today();

        CustomerTerm customerTerm = customerTermRepository.findFirstByStartTermLessThanEqualAndEndTermGreaterThanEqual(today, today)
                .orElse(null);

        return customerTermResponseMapper.toDTO(customerTerm);
    }

    @Transactional(readOnly = true)
    public List<CustomerTermResponseDTO> getAllCustomerTerms() {
        return customerTermRepository.findAllByOrderByStartTermDesc().stream()
                .map(customerTermResponseMapper::toDTO)
                .toList();
    }

    @Transactional
    public CustomerTermResponseDTO createCustomerTerm(CustomerTermRequestDTO dto) {
        validatePeriod(dto.startTerm(), dto.endTerm());

        if (customerTermRepository.existsOverlapping(dto.startTerm(), dto.endTerm())) {
            throw new HasAlreadyTermDataExistsException("O período informado se sobrepõe a outro período letivo");
        }

        CustomerTerm customerTerm = customerTermRequestMapper.toEntity(dto);

        return customerTermResponseMapper.toDTO(customerTermRepository.save(customerTerm));
    }

    @Transactional
    public CustomerTermResponseDTO updateCustomerTerm(UUID customerTermId, CustomerTermUpdateDTO dto) {
        CustomerTerm customerTerm = findTerm(customerTermId);

        // datas enviadas ou, se omitidas, as atuais
        LocalDate start = dto.startTerm() != null ? dto.startTerm() : customerTerm.getStartTerm();
        LocalDate end = dto.endTerm() != null ? dto.endTerm() : customerTerm.getEndTerm();

        validatePeriod(start, end);

        if (customerTermRepository.existsOverlappingExcluding(start, end, customerTermId)) {
            throw new HasAlreadyTermDataExistsException("O período informado se sobrepõe a outro período letivo");
        }

        customerTermRequestMapper.updateFromDTO(dto, customerTerm);

        return customerTermResponseMapper.toDTO(customerTerm);
    }

    @Transactional
    public void deleteCustomerTerm(UUID customerTermId) {
        customerTermRepository.delete(findTerm(customerTermId));
    }

    private CustomerTerm findTerm(UUID customerTermId) {
        return customerTermRepository.findById(customerTermId)
                .orElseThrow(() -> new CustomerTermNotFoundException("Período letivo não encontrado"));
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new DomainValidationException("As datas de início e fim são obrigatórias");
        }
        if (!start.isBefore(end)) {
            throw new DomainValidationException("A data de início deve ser anterior à data de fim");
        }
    }

    private Administrator findAdmin() {
        return administratorRepository.findByEmail(getAuthenticatedUserEmail()).orElseThrow(() -> new AccessDeniedException("Administrator não encontrado"));
    }

    private Customer findCustomer() {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Customer ID não encontrado");
        }
        return customerRepository.findById(customerId).orElseThrow(() -> new AccessDeniedException("Customer não encontrado"));
    }

    private LocalDate today() {
        Customer customer = findCustomer();

        return LocalDate.now(ZoneId.of(customer.getTimeZone()));
    }
}

/*
* responsável por realizar processamentos de dados em prol do calendário de atividade do customer
* não possui controller próprio, todos os métodos são concentrados apenas no CustomerController
* */