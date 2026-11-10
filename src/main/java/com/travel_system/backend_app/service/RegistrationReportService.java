package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.CustomerMismatchException;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.exceptions.CustomerTermNotFoundException;
import com.travel_system.backend_app.exceptions.InactiveAccountException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationDeadlines;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationQueryLimits;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationSettings;
import com.travel_system.backend_app.model.dtos.request.CustomerRegistrationNotificationSettingsDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerTermResponseDTO;
import com.travel_system.backend_app.model.dtos.response.GraceRegistrationItemDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationDistributionDTO;
import com.travel_system.backend_app.model.dtos.response.RegistrationSummaryDTO;
import com.travel_system.backend_app.model.dtos.response.StalledRegistrationItemDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.projections.*;
import com.travel_system.backend_app.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;
import static java.util.Map.Entry.comparingByValue;

@Service
public class RegistrationReportService {

    private final AdministratorRepository administratorRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTermRepository customerTermRepository;
    private final TransportRegistrationRepository transportRegistrationRepository;
    private final DocumentRepository documentRepository;
    private final RegistrationRequestRepository registrationRequestRepository;

    private final UserProfileResolverService userProfileResolverService;
    private final CustomerTimeService customerTimeService;

    private static final String UNKNOWN_OWNER = "Titular não encontrado";
    private static final List<RegistrationRequestType> APPROVED_ENROLLMENT_TYPES = List.of(RegistrationRequestType.INITIAL, RegistrationRequestType.RENEWAL);

    public RegistrationReportService(AdministratorRepository administratorRepository, CustomerRepository customerRepository, CustomerTermRepository customerTermRepository, TransportRegistrationRepository transportRegistrationRepository, DocumentRepository documentRepository, RegistrationRequestRepository registrationRequestRepository, UserProfileResolverService userProfileResolverService, CustomerTimeService customerTimeService) {
        this.administratorRepository = administratorRepository;
        this.customerRepository = customerRepository;
        this.customerTermRepository = customerTermRepository;
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.documentRepository = documentRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.userProfileResolverService = userProfileResolverService;
        this.customerTimeService = customerTimeService;
    }

    /*
     * resumo geral das matrículas do Customer para gerencia do administrador
     * */

    @Transactional(readOnly = true)
    public RegistrationSummaryDTO getRegistrationSummary() {
        Administrator administrator = getAuthenticatedActiveAdministrator();
        UUID customerId = administrator.getCustomerId();

        // carrega as configurações de matrícula do customer
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        RegistrationSettings settings = RegistrationSettings.from(customer);

        RegistrationSummaryDTO.RegistrationSettingsInfo settingsInfo = new RegistrationSummaryDTO.RegistrationSettingsInfo(
                settings.gracePeriodDays(),
                settings.renewalWindowDays(),
                settings.blockWarningDays());

        /*
         * datas de referência (independem do período vigente: cada matrícula é comparada pelo próprio endTerm)
         * - graceLimit: período que terminou antes desta data já esgotou a carência
         * - windowLimit: período que termina até esta data já está com a janela de renovação aberta
         * */
        LocalDate today = customerTimeService.today();
        LocalDate graceLimit = today.minusDays(settings.gracePeriodDays());
        LocalDate windowLimit = today.plusDays(settings.renewalWindowDays());

        return new RegistrationSummaryDTO(
                buildStatusSummary(customerId, today, graceLimit),
                buildReviewQueueSummary(customerId),
                buildRenewalProgressSummary(customerId, windowLimit, graceLimit),
                buildContext(customerId, today, settingsInfo)
        );
    }

    @Transactional(readOnly = true)
    public RegistrationDistributionDTO getDistributionByTerm(UUID termId) {
        // valida o admin e resolve o período
        Administrator administrator = getAuthenticatedActiveAdministrator();
        UUID customerId = administrator.getCustomerId();

        CustomerTerm customerTerm = resolveTerm(termId);

        // verifica se a operação está sob o mesmo customer
        isSameCustomer(customerId, customerTerm.getCustomerId());

        // montagem do termDTO
        RegistrationDistributionDTO.TermDTO termDTO = buildTermInfo(customerTerm);

        // agrupa os parâmetros identicos em um método auxiliar
        RegistrationQueryParams params = buildApprovedEnrollmentFilter(customerId, customerTerm.getId());

        // totais para alunos e motoristas
        // inclui os cálculos "com rota" e "sem rota"
        RegistrationDistributionDTO.TotalsDTO totalsDTO = buildTotals(params);

        // constrói os cursos com agrupamento, ordenação, consultas, etc
        List<RegistrationDistributionDTO.ByInstitutionsDTO> institutions = buildInstitutionDistribution(params);

        // turnos
        List<RegistrationDistributionDTO.ByShiftsDTO> shifts = buildShiftDistribution(params);

        List<RegistrationDistributionDTO.ByInstitutionAndShiftDTO> institutionsByShifts = buildInstitutionShiftDistribution(params);

        List<RegistrationDistributionDTO.ByRouteDTO> routes = buildRouteDistribution(params);

        // ver qual está divergente p o erro do enum

        return new RegistrationDistributionDTO(termDTO, totalsDTO, institutions, shifts, institutionsByShifts, routes);
    }

    /*
     * lista as matrículas em carência (período terminado, ainda com acesso)
     * - onlyBlockingSoon: restringe às que estão dentro do prazo do aviso de bloqueio
     * - cada matrícula é comparada pelo próprio fim de período: não depende do período vigente
     * - ordenada por quem perde o acesso primeiro
     * */
    @Transactional(readOnly = true)
    public Page<GraceRegistrationItemDTO> listInGrace(boolean onlyBlockingSoon, Pageable pageable) {
        // valida admin e pega customer
        Administrator administrator = getAuthenticatedActiveAdministrator();
        UUID customerId = administrator.getCustomerId();

        // carrega as infos do customer sobre carência e bloqueio
        RegistrationSettings settings = getCustomerConfigurations(customerId);

        RegistrationQueryLimits registrationQueryLimits = RegistrationQueryLimits.of(today(), settings);

        /*
         * datas-limite (cada matrícula é comparada pelo próprio endTerm na consulta)
         * - em carência: graceLimit <= endTerm < today
         * - bloqueio próximo: endTerm + carência <= today + aviso → endTerm <= today + aviso − carência
         * - sem o filtro, blockingSoonLimit = today (não restringe nada além do "< today")
         * */
        LocalDate graceLimit = registrationQueryLimits.graceLimit();
        LocalDate blockingSoonLimit = registrationQueryLimits.blockingSoonLimit();

        // a ordenação é fixa na consulta; mantém apenas a página e o tamanho
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<GraceRegistrationView> page = transportRegistrationRepository.findInGrace(customerId, TransportRegistrationStatus.ACTIVE, graceLimit, today(), blockingSoonLimit, pageOnly);

        if (page.isEmpty()) {
            return Page.empty(pageOnly);
        }

        // nomes dos titulares da página, em lote
        Set<UUID> studentIds = page.stream().map(GraceRegistrationView::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> driverIds = page.stream().map(GraceRegistrationView::getDriverId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> ownerNames = userProfileResolverService.resolveNames(studentIds, driverIds);

        // renovação aberta de cada matrícula da página, em lote
        List<UUID> registrationIds = page.stream().map(GraceRegistrationView::getRegistrationId).toList();

        Map<UUID, RegistrationRequestStatus> openRequestStatus = registrationRequestRepository
                .findOpenByRegistrationIds(registrationIds, List.of(RegistrationRequestStatus.DRAFT, RegistrationRequestStatus.UNDER_REVIEW)).stream()
                .collect(Collectors.toMap(request -> request.getTransportRegistration().getId(), RegistrationRequest::getStatus));

        return page.map(row -> {
            boolean isStudent = row.getStudentId() != null;
            UUID ownerId = isStudent ? row.getStudentId() : row.getDriverId();

            RegistrationDeadlines registrationDeadlines = RegistrationDeadlines.of(row.getTermEnd(), settings);

            LocalDate graceEnd = registrationDeadlines.graceEnd();

            return new GraceRegistrationItemDTO(
                    row.getRegistrationId(),
                    row.getRegistrationNumber(),
                    isStudent ? OwnerType.STUDENT : OwnerType.DRIVER,
                    ownerNames.getOrDefault(ownerId, UNKNOWN_OWNER),
                    row.getTermEnd(),
                    graceEnd,
                    ChronoUnit.DAYS.between(today(), graceEnd),
                    toRenewalProgress(openRequestStatus.get(row.getRegistrationId()))
            );
        });
    }

    /*
     * lista as matrículas PENDING paradas há mais de minDays
     * - rascunho sem atividade: a pessoa começou e não enviou
     * - rejeição sem reenvio: a pessoa não corrigiu o que o admin apontou
     * - ordenada pelos casos mais antigos primeiro
     * */
    @Transactional(readOnly = true)
    public Page<StalledRegistrationItemDTO> listStalled(int minDays, Pageable pageable) {
        if (minDays < 1 || minDays > 365) {
            throw new IllegalArgumentException("minDays deve estar entre 1 e 365");
        }

        Administrator administrator = getAuthenticatedActiveAdministrator();
        UUID customerId = administrator.getCustomerId();

        // início do dia de (hoje − minDays), no fuso do sistema
        LocalDate today = customerTimeService.today();
        Instant cutoff = customerTimeService.startOfDay(today);

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        // busca as matrículas que estão "paradas" em pending
        Page<StalledRegistrationView> page = registrationRequestRepository.findStalled(
                customerId,
                TransportRegistrationStatus.PENDING,
                RegistrationRequestStatus.DRAFT,
                RegistrationRequestStatus.REJECTED,
                cutoff,
                pageOnly);

        if (page.isEmpty()) {
            return Page.empty(pageOnly);
        }

        // nomes dos titulares da página, em lote
        Set<UUID> studentIds = page.stream().map(StalledRegistrationView::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> driverIds = page.stream().map(StalledRegistrationView::getDriverId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> ownerNames = userProfileResolverService.resolveNames(studentIds, driverIds);

        // documentos já enviados (os documentos são apagados assim que a matricula é aprovada, ou seja, isso aqui só roda em DRAFT)
        List<UUID> draftRequestIds = page.stream()
                .filter(row -> row.getRequestStatus() == RegistrationRequestStatus.DRAFT)
                .map(StalledRegistrationView::getRequestId).toList();

        Map<UUID, Long> documentsByRequest = draftRequestIds.isEmpty() ? Map.of() : documentRepository.countByRegistrationRequestIds(draftRequestIds).stream()
                .collect(Collectors.toMap(RequestDocumentCountView::getRequestId, RequestDocumentCountView::getTotal));

        return page.map(row -> {
            boolean isStudent = row.getStudentId() != null;
            UUID ownerId = isStudent ? row.getStudentId() : row.getDriverId();

            StalledReason reason = row.getRequestStatus() == RegistrationRequestStatus.DRAFT ? StalledReason.DRAFT_NOT_SUBMITTED : StalledReason.REJECTED_NOT_RESUBMITTED;

            // dias parados, contados em datas do fuso do sistema
            long daysStalled = ChronoUnit.DAYS.between(customerTimeService.toLocalDate(row.getSince()), today);

            return new StalledRegistrationItemDTO(
                    row.getRegistrationId(),
                    isStudent ? OwnerType.STUDENT : OwnerType.DRIVER,
                    ownerNames.getOrDefault(ownerId, UNKNOWN_OWNER),
                    reason,
                    row.getSince(),
                    daysStalled,
                    documentsByRequest.getOrDefault(row.getRequestId(), 0L)
            );
        });
    }

    // situação da renovação a partir do status da solicitação aberta
    private RenewalProgressStatus toRenewalProgress(RegistrationRequestStatus openRequestStatus) {
        if (openRequestStatus == null) {
            return RenewalProgressStatus.NOT_STARTED;
        }

        return openRequestStatus == RegistrationRequestStatus.UNDER_REVIEW ? RenewalProgressStatus.UNDER_REVIEW : RenewalProgressStatus.DRAFT;
    }

    private List<RegistrationDistributionDTO.ByRouteDTO> buildRouteDistribution(RegistrationQueryParams params) {
        // detalhamento por período (period)
        List<RouteScheduleCountView> routeScheduleCountViews = registrationRequestRepository.studentsDetailsPerPeriodTrip(params.customerId, params.termId, params.status, params.types);

        // agrupando com base no ID e nos detalhes de cada
        Map<UUID, List<RegistrationDistributionDTO.RouteScheduleDTO>> hourMapping = routeScheduleCountViews.stream()
                .filter(id -> id.getStandardRouteId() != null)
                .collect(Collectors.groupingBy(
                        RouteScheduleCountView::getStandardRouteId,
                        Collectors.mapping(row ->
                                        new RegistrationDistributionDTO.RouteScheduleDTO(
                                                row.getTravelPeriod(),
                                                row.getTravelDirection(),
                                                row.getTotal()
                                        ),
                                Collectors.toList()
                        )
                ));

        // quantos alunos matrículados por período usam cada rota
        List<RouteCountView> routeCountViews = registrationRequestRepository.studentsPerPeriodAndRoute(params.customerId, params.termId, params.status, params.types);

        // converte para o DTO usando a lista de horário correspodnente ao id
        // caso não ache, retorna um arr vazio
        return routeCountViews.stream()
                .map(routeCountView -> {
                    List<RegistrationDistributionDTO.RouteScheduleDTO> hourMappingOrDefault = hourMapping.getOrDefault(routeCountView.getId(), List.of());

                    return new RegistrationDistributionDTO.ByRouteDTO(routeCountView.getId(), routeCountView.getStandardRouteName(), routeCountView.getTotal(), hourMappingOrDefault);
                })
                .sorted(Comparator.comparing(RegistrationDistributionDTO.ByRouteDTO::total).reversed())
                .toList();
    }

    private List<RegistrationDistributionDTO.ByInstitutionAndShiftDTO> buildInstitutionShiftDistribution(RegistrationQueryParams params) {
        // instituição com turnos
        List<InstitutionShiftCountView> institutionShiftCountViews = registrationRequestRepository.institutionShiftCount(params.customerId, params.termId, params.status, params.types);

        long withoutInstitutions = institutionShiftCountViews.stream()
                .filter(id -> id.getInstitutionId() == null)
                .mapToLong(InstitutionShiftCountView::getTotal).sum();

        // monta uma lista em que cada instituição tem os totais por turno
        Map<UUID, List<InstitutionShiftCountView>> institutionsTotalPerShift = institutionShiftCountViews.stream()
                .filter(id -> id.getInstitutionId() != null)
                .collect(Collectors.groupingBy(
                        InstitutionShiftCountView::getInstitutionId,
                        HashMap::new,
                        Collectors.toList()
                ));

        List<RegistrationDistributionDTO.ByInstitutionAndShiftDTO> institutionsByShifts = institutionsTotalPerShift.entrySet().stream()
                .map(entry -> {
                    List<InstitutionShiftCountView> rows = entry.getValue();

                    InstitutionShiftCountView first = rows.getFirst();

                    // turnos
                    List<RegistrationDistributionDTO.ByShiftsDTO> sfts = rows.stream()
                            .map(row -> new RegistrationDistributionDTO.ByShiftsDTO(row.getShifts(), row.getTotal())).toList();

                    return new RegistrationDistributionDTO.ByInstitutionAndShiftDTO(
                            entry.getKey(),
                            first.getInstitutionName(),
                            sfts);

                }).sorted(Comparator.comparing(RegistrationDistributionDTO.ByInstitutionAndShiftDTO::institutionName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (withoutInstitutions > 0) {
            institutionsByShifts = new ArrayList<>(institutionsByShifts);

            List<RegistrationDistributionDTO.ByShiftsDTO> shiftsWithoutInstitutions = institutionShiftCountViews.stream()
                    .filter(id -> id.getInstitutionId() == null)
                    .map(dto -> new RegistrationDistributionDTO.ByShiftsDTO(dto.getShifts(), dto.getTotal())).toList();

            institutionsByShifts.add(new RegistrationDistributionDTO.ByInstitutionAndShiftDTO(
                    null,
                    "Não informado",
                    shiftsWithoutInstitutions
            ));
        }
        
        return institutionsByShifts;
    }

    private List<RegistrationDistributionDTO.ByShiftsDTO> buildShiftDistribution(RegistrationQueryParams params) {
        // turnos
        List<ShiftCountView> countStudentShifts = registrationRequestRepository.getAndCountStudentShifts(params.customerId, params.termId, params.status, params.types);

        // aluno sem turno vira lista vazia
        return countStudentShifts.stream()
                .map(dto -> new RegistrationDistributionDTO.ByShiftsDTO(
                        dto.getShift(),
                        dto.getTotal()
                )).toList();
    }

    private List<RegistrationDistributionDTO.ByInstitutionsDTO> buildInstitutionDistribution(RegistrationQueryParams params) {
        // instituição e curso
        List<InstitutionCourseCountView> institutionsAndCoursesGroupedByType = registrationRequestRepository.getInstitutionsAndCoursesGroupedByType(params.customerId, params.termId, params.status, params.types);

        long studentsWithoutInstitutions = institutionsAndCoursesGroupedByType.stream()
                .filter(row -> row.getInstitutionId() == null)
                .mapToLong(InstitutionCourseCountView::getTotal)
                .sum();

        // agrupamento por instituição e cursos dentro de cada uma, já filtrando as insittuições null pelo hashMap
        Map<UUID, List<InstitutionCourseCountView>> rowInstitutionsWithCourses = institutionsAndCoursesGroupedByType.stream()
                .filter(id -> id.getInstitutionId() != null)
                .collect(Collectors.groupingBy(
                        InstitutionCourseCountView::getInstitutionId,
                        HashMap::new,
                        Collectors.toList()
                ));

        // agrupamento do total de alunos por instituição, do maior para o menor
        List<Map.Entry<UUID, List<InstitutionCourseCountView>>> sortedRowInstitutionsWithCourses = rowInstitutionsWithCourses.entrySet().stream()
                .sorted(Map.Entry.<UUID, List<InstitutionCourseCountView>>comparingByValue(Comparator.comparing(a -> a.stream()
                                .mapToLong(InstitutionCourseCountView::getTotal).sum()))
                        .reversed())
                .toList();

        List<RegistrationDistributionDTO.ByInstitutionsDTO> institutions = sortedRowInstitutionsWithCourses.stream()
                .map(entry -> {
                    List<InstitutionCourseCountView> rows = entry.getValue();

                    InstitutionCourseCountView first = rows.getFirst();

                    List<RegistrationDistributionDTO.CourseDistributionDTO> courses = rows.stream()
                            .filter(row -> row.getCourseId() != null)
                            .map(row -> new RegistrationDistributionDTO.CourseDistributionDTO(
                                    row.getCourseId(),
                                    row.getCourseName(),
                                    row.getTotal()
                            )).toList();

                    return new RegistrationDistributionDTO.ByInstitutionsDTO(
                            entry.getKey(),
                            first.getInstitutionName(),
                            first.getInstitutionType(),
                            courses,
                            rows.stream().mapToLong(InstitutionCourseCountView::getTotal).sum());
                }).toList();

        // se houver alunos sem instituição adc por último na lista de institutions
        if (studentsWithoutInstitutions > 0) {
            institutions = new ArrayList<>(institutions);

            institutions.add(new RegistrationDistributionDTO.ByInstitutionsDTO(
                    null,
                    "Não informado",
                    null,
                    List.of(),
                    studentsWithoutInstitutions
            ));
        }

        return institutions;
    }

    private RegistrationDistributionDTO.TotalsDTO buildTotals(RegistrationQueryParams params) {
        // pega os totais dos drivers e dos alunos agrupados por tipo e período
        DistributionTotalsView distributionTotalsView = registrationRequestRepository.countApprovedStudentsByTermAndGroupedByType(params.customerId, params.termId, params.status, params.types);

        // quantos alunos matrículados por período ainda não tem nenhuma rota
        Long studentsWithoutAnyRoute = registrationRequestRepository.countStudentsWithoutAnyRoute(params.customerId, params.termId, params.status, params.types);

        long studentsWithRoutesCount = distributionTotalsView.getStudents() - toLong(studentsWithoutAnyRoute);

        return new RegistrationDistributionDTO.TotalsDTO(
                distributionTotalsView.getStudents(),
                distributionTotalsView.getDrivers(),
                toLong(studentsWithoutAnyRoute),
                studentsWithRoutesCount);
    }

    // constroi o dto do customerTerm
    private RegistrationDistributionDTO.TermDTO buildTermInfo(CustomerTerm term) {
        boolean isCurrentTerm;

        isCurrentTerm = isCurrentPeriod(term);

        return new RegistrationDistributionDTO.TermDTO(new CustomerTermResponseDTO(
                term.getId(),
                term.getName(),
                term.getStartTerm().toString(),
                term.getEndTerm().toString()),
                isCurrentTerm);
    }

    // resolve o term
    private CustomerTerm resolveTerm(UUID termId) {
        CustomerTerm customerTerm;
        if (termId != null) {
            customerTerm = customerTermRepository.findById(termId)
                    .orElseThrow(() -> new CustomerTermNotFoundException("Período não encontrado"));

            // verifica se o período é o vigente
        } else {
            customerTerm = customerTermRepository.findByCurrentDate(customerTimeService.today())
                    .orElseGet(() -> customerTermRepository.findFirstByStartTermAfterOrderByStartTermAsc(customerTimeService.today()).orElse(null));

            if (customerTerm == null) {
                throw new CustomerTermNotFoundException("Nenhum período encontrado");
            }
        }

        return customerTerm;
    }

    // monta o filtro padrão usado em todas as consultas do registrationRequest
    private RegistrationQueryParams buildApprovedEnrollmentFilter(UUID customerId, UUID termId) {
        return new RegistrationQueryParams(
                customerId,
                termId,
                RegistrationRequestStatus.APPROVED,
                APPROVED_ENROLLMENT_TYPES
        );
    }

    /*
     * situação das matrículas
     * */
    private RegistrationSummaryDTO.RegistrationStatusSummary buildStatusSummary(UUID customerId, LocalDate today, LocalDate graceLimit) {
        // contagem por status (se status ausente no resultado = 0)
        Map<TransportRegistrationStatus, Long> countsByStatus = transportRegistrationRepository.countGroupedByStatus(customerId).stream()
                .collect(Collectors.toMap(StatusCountView::getStatus, StatusCountView::getTotal));

        // faixas das matrículas ACTIVE
        ActiveRangeCountView ranges = transportRegistrationRepository.countActiveByTermEndRange(customerId, today, graceLimit, TransportRegistrationStatus.ACTIVE);

        long activeStudents = toLong(ranges.getActiveStudents());
        long activeDrivers = toLong(ranges.getActiveDrivers());
        long graceStudents = toLong(ranges.getGraceStudents());
        long graceDrivers = toLong(ranges.getGraceDrivers());

        return new RegistrationSummaryDTO.RegistrationStatusSummary(
                new RegistrationSummaryDTO.OwnerTypeCount(activeStudents + activeDrivers, activeStudents, activeDrivers),
                new RegistrationSummaryDTO.OwnerTypeCount(graceStudents + graceDrivers, graceStudents, graceDrivers),
                toLong(ranges.getAwaitingClosure()),
                countsByStatus.getOrDefault(TransportRegistrationStatus.PENDING, 0L),
                countsByStatus.getOrDefault(TransportRegistrationStatus.CLOSED, 0L),
                countsByStatus.getOrDefault(TransportRegistrationStatus.INACTIVE, 0L)
        );
    }

    /*
     * fila de análise (solicitações em UNDER_REVIEW)
     * */
    private RegistrationSummaryDTO.ReviewQueueSummary buildReviewQueueSummary(UUID customerId) {
        List<ReviewQueueCountView> rows = registrationRequestRepository.countUnderReviewGroupedByType(customerId, RegistrationRequestStatus.UNDER_REVIEW);

        Map<RegistrationRequestType, Long> countsByType = rows.stream()
                .collect(Collectors.toMap(ReviewQueueCountView::getType, ReviewQueueCountView::getTotal));

        long initial = countsByType.getOrDefault(RegistrationRequestType.INITIAL, 0L);
        long renewal = countsByType.getOrDefault(RegistrationRequestType.RENEWAL, 0L);
        long update = countsByType.getOrDefault(RegistrationRequestType.UPDATE, 0L);

        // envio mais antigo ainda aguardando (null se a fila estiver vazia)
        Instant oldestSubmittedAt = rows.stream().map(ReviewQueueCountView::getOldestSubmittedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new RegistrationSummaryDTO.ReviewQueueSummary(initial + renewal + update, initial, renewal, update, oldestSubmittedAt);
    }

    /*
     * andamento da renovação (janela aberta, ainda dentro da carência)
     * */
    private RegistrationSummaryDTO.RenewalProgressSummary buildRenewalProgressSummary(UUID customerId, LocalDate windowLimit, LocalDate graceLimit) {
        RenewalProgressView progress = transportRegistrationRepository.countRenewalProgress(
                customerId, windowLimit, graceLimit,
                TransportRegistrationStatus.ACTIVE,
                RegistrationRequestType.RENEWAL,
                RegistrationRequestStatus.DRAFT,
                RegistrationRequestStatus.UNDER_REVIEW);

        long notStarted = toLong(progress.getNotStarted());
        long inDraft = toLong(progress.getInDraft());
        long underReview = toLong(progress.getUnderReview());

        /*
         * matrículas que não conseguem renovar porque o próximo período não foi cadastrado
         * - uma verificação por data de fim distinta (normalmente apenas uma: a do período vigente)
         * */
        long blockedByMissingTerm = transportRegistrationRepository
                .countOpenWindowGroupedByTermEnd(customerId, windowLimit, graceLimit, TransportRegistrationStatus.ACTIVE).stream()
                .filter(row -> !customerTermRepository.existsByCustomerIdAndStartTermAfter(customerId, row.getEndTerm()))
                .mapToLong(TermEndCountView::getTotal)
                .sum();

        return new RegistrationSummaryDTO.RenewalProgressSummary(
                notStarted + inDraft + underReview, notStarted, inDraft, underReview, blockedByMissingTerm);
    }

    /*
     * contexto do cálculo
     * - período vigente é opcional (nas férias não há período vigente, o painel não pode falhar por isso)
     * */
    private RegistrationSummaryDTO.SummaryContext buildContext(UUID customerId, LocalDate today, RegistrationSummaryDTO.RegistrationSettingsInfo settings) {
        Optional<CustomerTerm> currentTerm = customerTermRepository.findByCurrentDate(today);

        RegistrationSummaryDTO.CurrentTermInfo currentTermInfo = currentTerm
                .map(term -> new RegistrationSummaryDTO.CurrentTermInfo(term.getId(), term.getName(), term.getStartTerm(), term.getEndTerm()))
                .orElse(null);

        boolean nextTermRegistered = currentTerm
                .map(term -> customerTermRepository.existsByCustomerIdAndStartTermAfter(customerId, term.getEndTerm()))
                .orElse(false);

        return new RegistrationSummaryDTO.SummaryContext(today, currentTermInfo, nextTermRegistered, settings);
    }

    // recupera as configs do customer sobre prazos
    private RegistrationSettings getCustomerConfigurations(UUID customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return RegistrationSettings.from(customer);
    }

    private Administrator getAuthenticatedActiveAdministrator() {
        Administrator administrator = administratorRepository.findByEmail(getAuthenticatedUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Admin não encontrado"));

        if (administrator.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Administrador inativo no sistema");
        }

        UUID admCustomerId = administrator.getCustomerId();
        UUID customerId = TenantContext.getCurrentTenant();

        if (!admCustomerId.equals(customerId)) {
            throw new CustomerMismatchException("Customers não são iguais, nao é possível prosseguir");
        }

        return administrator;
    }

    private void isSameCustomer(UUID customerId, UUID customerIdTwo) {
        if (!customerId.equals(customerIdTwo)) {
            throw new CustomerMismatchException("Os customers divergem, não é possível prosseguir");
        }
    }

    private LocalDate today() {
        return customerTimeService.today();
    }

    private boolean isCurrentPeriod(CustomerTerm customerTerm) {
        return !customerTimeService.today().isBefore(customerTerm.getStartTerm()) && !customerTimeService.today().isAfter(customerTerm.getEndTerm());
    }

    private long toLong(Long value) {
        return value != null ? value : 0L;
    }

    private record RegistrationQueryParams(
            UUID customerId,
            UUID termId,
            RegistrationRequestStatus status,
            List<RegistrationRequestType> types
    ) {}
}
