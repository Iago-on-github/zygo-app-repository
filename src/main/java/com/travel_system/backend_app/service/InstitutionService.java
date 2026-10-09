package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.InstitutionRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.InstitutionCourseResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.response.InstitutionResponseMapper;
import com.travel_system.backend_app.model.Institution;
import com.travel_system.backend_app.model.InstitutionCourse;
import com.travel_system.backend_app.model.dtos.invitation.student.InstitutionCatalogResponseDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.repository.InstitutionCourseRepository;
import com.travel_system.backend_app.repository.InstitutionRepository;
import com.travel_system.backend_app.repository.StudentEnrollmentRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

@Service
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;
    private final InstitutionCourseRepository institutionCourseRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;

    private final InstitutionRequestMapper institutionRequestMapper;
    private final InstitutionResponseMapper institutionResponseMapper;

    private final InstitutionCourseResponseMapper institutionCourseResponseMapper;

    public InstitutionService(InstitutionRepository institutionRepository, StudentRepository studentRepository, InstitutionCourseRepository institutionCourseRepository, StudentEnrollmentRepository studentEnrollmentRepository, InstitutionRequestMapper institutionRequestMapper, InstitutionResponseMapper institutionResponseMapper, InstitutionCourseResponseMapper institutionCourseResponseMapper) {
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.institutionCourseRepository = institutionCourseRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.institutionRequestMapper = institutionRequestMapper;
        this.institutionResponseMapper = institutionResponseMapper;
        this.institutionCourseResponseMapper = institutionCourseResponseMapper;
    }

    /*
    * recupera todas as instituições com base em filtros opcionais
    * "course" funciona pela similaridade
    * */
    @Transactional(readOnly = true)
    public Page<InstitutionResponseDTO> getAllInstitutions(String institutionName, GeneralStatus status, InstitutionType institutionType, String course, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        // a ordem vem da query (relevância); só página e tamanho são repassados
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return institutionRepository.findAllByOptionalFilters(
                        customerId,
                        status != null ? status.name() : null,
                        blankToNull(institutionName),
                        institutionType != null ? institutionType.name() : null,
                        blankToNull(course),
                        pageOnly)
                .map(institutionResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public InstitutionResponseDTO getInstitutionById(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .map(institutionResponseMapper::toDTO)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada"));
    }

    @Transactional(readOnly = true)
    public List<InstitutionResponseDTO> getInstitutionsByStudent(UUID studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new EntityNotFoundException("Estudante não encontrado");
        }

        return institutionRepository.findAllByStudentId(studentId, GeneralStatus.ACTIVE).stream()
                .map(institutionResponseMapper::toDTO)
                .toList();
    }

    // recupera todos os cursos de uma instituição
    @Transactional(readOnly = true)
    public Page<InstitutionCourseResponseDTO> getAllInstitutionCourses(UUID institutionId, Pageable pageable) {
        if (!institutionRepository.existsById(institutionId)) {
            throw new InstitutionNotFoundException("Instituição não existe");
        }

        return institutionCourseRepository.findAllByInstitutionId(institutionId, pageable).map(institutionCourseResponseMapper::toDTO);
    }

    // usado no service/controller de invitation para exibir ao estudante as instituições e cursos
    @Transactional(readOnly = true)
    public List<InstitutionCatalogResponseDTO> getActiveCatalogByCustomer(UUID customerId) {
        List<Institution> institutions = institutionRepository.findActiveByCustomerIdIgnoringTenant(customerId);

        if (institutions.isEmpty()) {
            return List.of();
        }

        // cursos ativos de todas as instituições do customer, agrupados pelo ID e pela instituição
        Map<UUID, List<InstitutionCatalogResponseDTO.CatalogCourseDTO>> coursesByInstitution = institutionCourseRepository.findActiveByCustomerIdIgnoringTenant(customerId).stream()
                .collect(Collectors.groupingBy(
                        course -> course.getInstitution().getId(),
                        Collectors.mapping(course -> new InstitutionCatalogResponseDTO.CatalogCourseDTO(course.getId(), course.getName()),
                                Collectors.toList())));

        return institutions.stream()
                .map(institution -> new InstitutionCatalogResponseDTO(
                        institution.getId(),
                        institution.getInstitutionName(),
                        institution.getInstitutionType(),
                        coursesByInstitution.getOrDefault(institution.getId(), List.of())
                )).toList();
    }

    @Transactional
    public InstitutionResponseDTO createInstitution(InstitutionRequestDTO dto) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        Institution institution = institutionRequestMapper.toEntity(dto);
        institution.setInstitutionName(dto.institutionName().trim());

        // mantém o nome como digitado (com trim) e elimina duplicados ignorando maiúsculas
        Map<String, String> uniqueCourses = new LinkedHashMap<>();
        dto.courses().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(course -> !course.isEmpty())
                .forEach(course -> uniqueCourses.putIfAbsent(course.toLowerCase(Locale.ROOT), course));

        uniqueCourses.values().forEach(institution::addCourse);

        return institutionResponseMapper.toDTO(institutionRepository.save(institution));
    }

    @Transactional
    public InstitutionResponseDTO updateInstitution(UUID institutionId, InstitutionUpdateDTO dto) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada"));

        institutionRequestMapper.updateFromDTO(dto, institution);

        return institutionResponseMapper.toDTO(institutionRepository.save(institution));
    }

    @Transactional
    public InstitutionCourseResponseDTO addCourse(UUID institutionId, InstitutionCourseRequestDTO dto) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada"));

        String courseName = dto.courseName().trim();

        Optional<InstitutionCourse> existing = institution.getCourses().stream()
                .filter(course -> course.getName().equalsIgnoreCase(courseName))
                .findFirst();

        if (existing.isPresent()) {
            InstitutionCourse course = existing.get();
            if (course.getStatus() == GeneralStatus.ACTIVE) {
                throw new CourseAlreadyExistsException("Esse curso já existe nesta instituição");
            }
            // mesmo nome, mas inativo: reativa em vez de criar outro
            course.setStatus(GeneralStatus.ACTIVE);
            return institutionCourseResponseMapper.toDTO(course);
        }

        InstitutionCourse course = institution.addCourse(courseName);

        try {
            institutionCourseRepository.saveAndFlush(course);
        } catch (DataIntegrityViolationException e) {
            throw new CourseAlreadyExistsException("Esse curso já existe nesta instituição");
        }

        return institutionCourseResponseMapper.toDTO(course);
    }

    @Transactional
    public void removeCourse(UUID institutionId, UUID courseId) {
        InstitutionCourse course = institutionCourseRepository.findByIdAndInstitutionId(courseId, institutionId)
                .orElseThrow(() -> new CourseNotFoundException("Curso não encontrado"));

        // com matrículas: apenas inativa, preservando o histórico dos alunos
        if (studentEnrollmentRepository.existsByCourseId(courseId)) {
            course.setStatus(GeneralStatus.INACTIVE);
            return;
        }

        // sem matrículas: remove da coleção; o orphanRemoval apaga o registro no commit
        course.getInstitution().getCourses().remove(course);
    }

    @Transactional
    public void reactivateCourse(UUID institutionId, UUID courseId) {
        InstitutionCourse course = institutionCourseRepository.findByIdAndInstitutionId(courseId, institutionId)
                .orElseThrow(() -> new CourseNotFoundException("Curso não encontrado"));

        course.setStatus(GeneralStatus.ACTIVE);
    }

    @Transactional
    public void updateInstitutionStatus(UUID institutionId, UpdateStatusDTO dto) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada"));

        if (institution.getStatus() == dto.status()) {
            throw new DuplicateResourceException("A instituição já está com esse status");
        }

        institution.setStatus(dto.status());

        institutionRepository.save(institution);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

}
