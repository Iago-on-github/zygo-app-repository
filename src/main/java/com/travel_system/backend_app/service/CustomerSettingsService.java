package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.interfaces.mappers.CustomerRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerHolidayResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.response.CustomerResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.CustomerContactUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerSettingsUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.core.Local;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CustomerSettingsService {
    private final Logger log = LoggerFactory.getLogger(CustomerSettingsService.class);

    private final CustomerRepository customerRepository;
    private final AdministratorRepository administratorRepository;
    private final StandardRouteRepository standardRouteRepository;
    private final S3StorageService storageService;
    private final CustomerHolidayRepository customerHolidayRepository;
    private final CustomerTermRepository customerTermRepository;

    private final AdministratorService administratorService;
    private final DriverService driverService;
    private final StudentService studentService;

    private final CustomerRequestMapper customerRequestMapper;
    private final CustomerResponseMapper customerResponseMapper;

    public CustomerSettingsService(CustomerRepository customerRepository, AdministratorRepository administratorRepository, StandardRouteRepository standardRouteRepository, RouteStopRepository routeStopRepository, CustomerHolidayResponseMapper customerHolidayResponseMapper, S3StorageService storageService, CustomerHolidayRepository customerHolidayRepository, CustomerTermRepository customerTermRepository, AdministratorService administratorService, DriverService driverService, StudentService studentService, CustomerRequestMapper customerRequestMapper, CustomerResponseMapper customerResponseMapper) {
        this.customerRepository = customerRepository;
        this.administratorRepository = administratorRepository;
        this.standardRouteRepository = standardRouteRepository;
        this.storageService = storageService;
        this.customerHolidayRepository = customerHolidayRepository;
        this.customerTermRepository = customerTermRepository;
        this.administratorService = administratorService;
        this.driverService = driverService;
        this.studentService = studentService;
        this.customerRequestMapper = customerRequestMapper;
        this.customerResponseMapper = customerResponseMapper;
    }

    @Transactional(readOnly = true)
    public CustomerSettingsResponseDTO getMyCustomer() {
        Customer customer = findCustomer();

        return customerResponseMapper.toSettingsDTO(customer);
    }

    @Transactional(readOnly = true)
    public CustomerPlanResponseDTO getPlanUsage() {
        Customer customer = findCustomer();
        CustomerPlan plan = customer.getPlan();

        long adminsCount = administratorService.countAdministratorInThisCustomer(customer.getId());
        long driversCount = driverService.countDriversInThisCustomer(customer.getId());
        long studentsCount = studentService.countStudentsInThisCustomer(customer.getId());

        CustomerPlanUsageResponseDTO planUsage = new CustomerPlanUsageResponseDTO(plan.getMaxAdministrators(), plan.getMaxDrivers(), plan.getMaxStudents());

        return new CustomerPlanResponseDTO(plan, adminsCount, driversCount, studentsCount, planUsage);
    }

    /*
    * provê aos users informações sobre o customer, como:
    * calendários, rotas e períodos
    * */
    @Transactional
    public CustomerInfoResponseDTO getCustomerInfo() {
        Customer customer = findCustomer();

        // pega as rotas ativas com suas respectivas paradas em ordem
        List<CustomerInfoResponseDTO.StandardRouteDTO> standardRoutes = standardRouteRepository.findAllByStatus(GeneralStatus.ACTIVE).stream()
                .map(this::toStandardRouteDTO).toList();

        // pega os próximos 30 dias de feriados e período atual
        List<CustomerHoliday> upcomingHolidays = customerHolidayRepository.findAllByDateBetweenOrderByDateAsc(today(), today().plusDays(30));

        CustomerTerm currentTerm = customerTermRepository.findFirstByStartTermLessThanEqualAndEndTermGreaterThanEqual(today(), today())
                .orElse(null);

        return new CustomerInfoResponseDTO(
                customer.getName(),
                storageService.getPublicUrl(customer.getLogoUrl()),
                customer.getCity().getName(),
                customer.getContactEmail(),
                customer.getContactTelephone(),
                toAddressDTO(customer.getAddress()),
                customer.getShifts(),
                customer.getOperatingDays(),
                customer.getTimeZone(),
                standardRoutes,

                resolveOperatingStatus(customer, currentTerm, upcomingHolidays),

                currentTerm == null ? null : new CustomerInfoResponseDTO.TermDTO(currentTerm.getName(), currentTerm.getStartTerm(), currentTerm.getEndTerm()),

                upcomingHolidays.stream().map(h -> new CustomerInfoResponseDTO.HolidayDTO(h.getDate(), h.getDescription(), h.getShifts()))
                        .toList()
        );

    }

    @Transactional
    public CustomerSettingsResponseDTO updateMyContact(CustomerContactUpdateDTO dto) {
        Customer customer = findCustomer();

        // verificação de duplicidade global ignorando o customer atual
        if (dto.contactEmail() != null && !dto.contactEmail().isBlank()) {
            if (!dto.contactEmail().equals(customer.getContactEmail())) {
                if (customerRepository.existsByContactEmailIgnoringTenant(dto.contactEmail())) {
                    throw new DuplicateResourceException("Esse email já está em uso no sistema");
                }
            }
        }

        if (dto.contactTelephone() != null && !dto.contactTelephone().isBlank()) {
            if (!dto.contactTelephone().equals(customer.getContactTelephone())) {
                if (customerRepository.existsByContactTelephoneIgnoringTenant(dto.contactTelephone())) {
                    throw new DuplicateResourceException("Esse telefone já está em uso no sistema");
                }
            }
        }

        customerRequestMapper.updateMyContactFromDTO(dto, customer);

        return customerResponseMapper.toSettingsDTO(customerRepository.save(customer));
    }

    @Transactional
    public CustomerSettingsResponseDTO updateMySettings(CustomerSettingsUpdateDTO dto) {
        Customer customer = findCustomer();

        log.info("[1]settings recebidos: shifts={}, operatingDays={}", dto.shifts(), dto.operatingDays());

        customerRequestMapper.updateSettingsFromDTO(dto, customer);

        log.info("[2]settings recebidos: shifts={}, operatingDays={}", dto.shifts(), dto.operatingDays());

        return customerResponseMapper.toSettingsDTO(customer);
    }

    private Customer findCustomer() {
        Administrator administrator = administratorRepository.findByEmail(getAuthenticatedUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Administrador não encontrado"));

        return customerRepository.findById(administrator.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(findCustomer().getTimeZone()));
    }

    // rota padrão e pontos de parada
    private CustomerInfoResponseDTO.StandardRouteDTO toStandardRouteDTO(StandardRoute standardRoute) {
        List<CustomerInfoResponseDTO.RouteStopDTO> routeStops = standardRoute.getRouteStopAssignments().stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getTravelDirection)
                        .thenComparingInt(RouteStopAssignment::getSequence))
                .map(assignment -> {
                    RouteStop stop = assignment.getRouteStop();
                    return new CustomerInfoResponseDTO.RouteStopDTO(
                            stop.getName(),
                            stop.getDescription(),
                            stop.getLatitude(),
                            stop.getLongitude(),
                            assignment.getTravelDirection(),
                            assignment.getSequence(),
                            assignment.isOptionalSpot()
                    );
                }).toList();

        return new CustomerInfoResponseDTO.StandardRouteDTO(
                standardRoute.getRouteName(),
                standardRoute.getRouteDescription(),
                standardRoute.getTravelPeriods(),
                routeStops);
    }

    // período letivo, dia da semana e feriado
    private CustomerInfoResponseDTO.OperatingStatusDTO resolveOperatingStatus(Customer customer, CustomerTerm currentTerm, List<CustomerHoliday> upcomingHolidays) {
        if (currentTerm == null) {
            return new CustomerInfoResponseDTO.OperatingStatusDTO(today(), false, "Fora do período letivo");
        }
        if (!customer.getOperatingDays().contains(today().getDayOfWeek())) {
            return new CustomerInfoResponseDTO.OperatingStatusDTO(today(), false, "Sem transporte neste dia da semana");
        }

        // pega o feriado de hoje
        Optional<CustomerHoliday> holidayToday = upcomingHolidays.stream().filter(h -> h.getDate().equals(today())).findFirst();

        if (holidayToday.isPresent()) {
            CustomerHoliday holiday = holidayToday.get();

            // se nao consta turnos, considera como o dia inteiro e com turnos só esses turnos ficam sem transporte
            if (holiday.getShifts().isEmpty()) {
                return new CustomerInfoResponseDTO.OperatingStatusDTO(today(), false, holiday.getDescription());
            }

            return new CustomerInfoResponseDTO.OperatingStatusDTO(today(), true, "Sem transporte nos turnos " + holiday.getShifts() + ": " + holiday.getDescription());
        }

        return new CustomerInfoResponseDTO.OperatingStatusDTO(today(), true, null);
    }

    private AddressResponseDTO toAddressDTO(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponseDTO(address.getId(), address.getStreet(), address.getNumber(),
                address.getNeighborhood(), address.getCity(), address.getCep(), address.getComplement());
    }
}

/*
* service responsável pelas configuraçoes do customer através do administrador
* */
