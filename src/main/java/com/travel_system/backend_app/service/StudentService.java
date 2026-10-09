package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.StudentRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.InstitutionResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.response.StudentResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentAcceptDTO;
import com.travel_system.backend_app.model.dtos.invitation.student.StudentProfileDTO;
import com.travel_system.backend_app.model.dtos.request.InstitutionRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultLinkRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentInstitutionRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.config.constants.ResponsibleAdultConstants.MAX_STUDENTS_PER_RESPONSIBLE_ADULT;
import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class StudentService {
    private final StudentRepository studentRepository;
    private final InvitationRepository invitationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ResponsibleAdultRepository responsibleAdultRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionCourseRepository institutionCourseRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final CustomerRepository customerRepository;

    private final PasswordEncoder passwordEncoder;

    private final StudentResponseMapper studentResponseMapper;
    private final StudentRequestMapper studentRequestMapper;

    public StudentService(StudentRepository studentRepository, PermissionsRepository permissionsRepository, InvitationRepository invitationRepository, UserAccountRepository userAccountRepository, ResponsibleAdultRepository responsibleAdultRepository, InstitutionRepository institutionRepository, PasswordEncoder passwordEncoder, StudentResponseMapper studentResponseMapper, StudentRequestMapper studentRequestMapper, InstitutionResponseMapper institutionResponseMapper, InstitutionCourseRepository institutionCourseRepository, StudentEnrollmentRepository studentEnrollmentRepository, CustomerRepository customerRepository) {
        this.studentRepository = studentRepository;
        this.invitationRepository = invitationRepository;
        this.userAccountRepository = userAccountRepository;
        this.responsibleAdultRepository = responsibleAdultRepository;
        this.institutionRepository = institutionRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentResponseMapper = studentResponseMapper;
        this.studentRequestMapper = studentRequestMapper;
        this.institutionCourseRepository = institutionCourseRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<StudentResponseDTO> getAllStudents(String email, String name, String lastName, String neighborhood, String institutionName, InstitutionType institutionType, Shift shift, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        String normalizedEmail = blankToNull(email);
        if (normalizedEmail != null) {
            normalizedEmail = normalizedEmail.toLowerCase(Locale.ROOT);
        }

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return studentRepository.findAllByOptionalFilters(
                        customerId,
                        normalizedEmail,
                        blankToNull(name),
                        blankToNull(lastName),
                        blankToNull(neighborhood),
                        blankToNull(institutionName),
                        institutionType != null ? institutionType.name() : null,
                        shift != null ? shift.name() : null,
                        pageOnly)
                .map(studentResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<StudentResponseDTO> getAllStudentsByInstitution(UUID institutionId, Set<UUID> courseIds, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new IllegalArgumentException("Usuário sem customer ID");
        }

        boolean filterByCourses = courseIds != null && !courseIds.isEmpty();
        Collection<UUID> courses = filterByCourses ? courseIds : List.of(institutionId);

        PageRequest pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return studentRepository.findAllByInstitution(customerId, institutionId, filterByCourses, courses, pageOnly).map(studentResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public StudentResponseDTO getStudentById(UUID studentId) {
        return studentResponseMapper.toDTO(studentRepository.findById(studentId).orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado")));
    }

    @Transactional(readOnly = true)
    public StudentResponseDTO getStudentByCpf(String cpf) {
        return studentResponseMapper.toDTO(studentRepository.findByCpf(normalizeField(cpf))
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado")));
    }

    @Transactional(readOnly = true)
    public Page<StudentResponseDTO> getStudentsByStatus(GeneralStatus status, Pageable pageable) {
        if (status == null) status = GeneralStatus.ACTIVE;

        Page<Student> students = studentRepository.findAllByStatus(status, pageable);

        return students.map(studentResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public StudentResponseDTO getCurrentStudent() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student student = studentRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrato: " + authenticatedUserEmail));

        return studentResponseMapper.toDTO(student);
    }

    public Student createForExistingAccount(UserAccount userAccount, UUID customerId, StudentProfileDTO studentProfileDTO) {
        StudentAcceptDTO accept = studentProfileDTO.studentAccept();

        // unicidade global (queries nativas)
        if (studentRepository.existsByTelephoneIgnoringTenant(accept.telephone())) {
            throw new DuplicateResourceException("Já existe um estudante com esse telefone");
        }
        if (accept.cpf() != null && studentRepository.existsByCpfIgnoringTenant(accept.cpf())) {
            throw new DuplicateResourceException("Já existe um estudante com esse CPF");
        }
        if (studentRepository.existsByUserAccountIdIgnoringTenant(userAccount.getId())) {
            throw new DuplicateResourceException("Esta conta já possui um perfil de estudante");
        }

        // limite do plano
        long countStudents = countStudentsInThisCustomer(customerId);

        if (countStudents >= studentRegisterLimit(customerId)) {
            throw new EntityLimitExceededException("O limite de cadastro para Estudantes no seu plano é de " + studentRegisterLimit(customerId) + ". Para mais cadastros faça um upgrade ou personalize seu plano.");
        }

        Student student = studentRequestMapper.toEntity(studentProfileDTO);

        student.setStatus(GeneralStatus.ACTIVE);
        student.setUserAccount(userAccount);
        student.assignCustomer(customerId);
        student.getStudentShift().addAll(studentProfileDTO.studentInvitation().studentShifts());

        // matrículas nos cursos escolhidos
        StudentInstitutionRequestDTO studentInstitutionRequestDTO = accept.studentInstitutionRequest();
        if (studentInstitutionRequestDTO != null) {
            enrollmentCourses(student, customerId, studentInstitutionRequestDTO);
        }

        return studentRepository.save(student);
    }

    @Transactional
    public void addResponsibleAdult(ResponsibleAdultLinkRequestDTO dto) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student student = studentRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado: " + authenticatedUserEmail));

        if (student.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Estudante inativo no sistema: " + authenticatedUserEmail);
        }

        // um estudante só pode possuir um responsável.
        // caso já possua, a alteração deve ser feita através do fluxo de transferência por parte do responsável ou, em casos de estuadntes > 18, o auto desvínculo
        if (student.getResponsibleAdult() != null) {
            throw new StudentAlreadyHasResponsibleAdultException("O estudante já possui um responsável vinculado");
        }

        UUID responsibleAdultId = dto.responsibleAdultId();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findById(responsibleAdultId)
                .orElseThrow(() -> new EntityNotFoundException("Responsável não encontrado pelo ID: " + responsibleAdultId));

        // valida mesmo customer
        validateSameCustomer(student.getCustomerId(), responsibleAdult.getCustomerId());

        if (responsibleAdult.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Responsável inativo no sistema");
        }

        if (responsibleAdult.getBirthdate() == null) {
            throw new IllegalStateException("O responsável não possui data de nascimento cadastrada");
        }

        int responsibleAdultAge = Period.between(responsibleAdult.getBirthdate(), LocalDate.now()).getYears();

        // O responsável precisa ser maior de idade.
        if (responsibleAdultAge < 18) {
            throw new UnderageResponsibleAdultException("O responsável deve ser maior de idade");
        }

        if (student.getBirthdate() == null) {
            throw new IllegalStateException("O estudante não possui data de nascimento cadastrada");
        }

        long currentStudents = studentRepository.countByResponsibleAdultId(responsibleAdult.getId());

        if (currentStudents >= MAX_STUDENTS_PER_RESPONSIBLE_ADULT) {
            throw new ResponsibleAdultStudentLimitExceededException("O responsável atingiu a quantidade máxima permitida de estudantes");
        }

        student.setResponsibleAdult(responsibleAdult);
        student.setStudentRelationshipType(dto.studentRelationshipType());
    }

    @Transactional
    public StudentResponseDTO updateCurrentStudent(StudentUpdateDTO studentUpdateDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student studentEntity = studentRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado, " + authenticatedUserEmail));

        if (studentEntity.getStatus() == GeneralStatus.INACTIVE) {
          throw new InactiveAccountModificationException("Não é possível modificar dados de uma conta inativa: " + authenticatedUserEmail);
        }

        UserAccount userAccount = studentEntity.getUserAccount();

        // verifica se email já existe
        if (studentUpdateDTO.email() != null && !studentUpdateDTO.email().isBlank()) {
            if (!studentUpdateDTO.email().equals(userAccount.getEmail())) {
                if (userAccountRepository.existsByEmail(studentUpdateDTO.email())) {
                    throw new DuplicateResourceException("Email já em uso por outro usuário.");
                }
            }
        }

        // verifica se telefone já existe
        if (studentUpdateDTO.telephone() != null && !studentUpdateDTO.telephone().isBlank()) {
            if (!studentUpdateDTO.telephone().equals(studentEntity.getTelephone())) {
                if (studentRepository.existsByTelephoneIgnoringTenant(studentUpdateDTO.telephone())) {
                    throw new DuplicateResourceException("Telefone já em uso por outro usuário.");
                }
            }
        }

        // atualiza parcialmente sempre ignorando a senha
        studentRequestMapper.studentUpdateFromDTO(studentUpdateDTO, studentEntity);

        // senha atualiza manualmente por conta do encrypt
        if (studentUpdateDTO.password() != null && !studentUpdateDTO.password().isBlank()) {
            userAccount.setPassword(passwordEncoder.encode(studentUpdateDTO.password()));
        }
        
        Student savedStudent = studentRepository.save(studentEntity);

        return studentResponseMapper.toDTO(savedStudent);
    }

    @Transactional
    public void removeResponsibleAdult() {
        // se o estudante for menor, lançar exception e essa responsabilidade com a parte do responsável

        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student studentEntity = studentRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado, " + authenticatedUserEmail));

        if (studentEntity.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Estudante inativo no sistema: " + authenticatedUserEmail);
        }

        // calcula a idade do estudante
        int studentAge = Period.between(studentEntity.getBirthdate(), LocalDate.now()).getYears();

        // estudantes menores de idade não podem se auto-desvincular de um responsável
        if (studentAge < 18) {
           throw new MinorStudentResponsibleAdultTransferRequiredException("Estudante menor de idade necessita de um responsável vinculado");
        }

        // realiza a remoção
        studentEntity.setResponsibleAdult(null);

        studentRepository.save(studentEntity);
    }

    @Transactional
    public void updateStudentStatus(GeneralStatus newStatus) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student student = studentRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado para o email: " + authenticatedUserEmail));

        if (student.getStatus() == newStatus) {
            throw new DuplicateResourceException("Estudante já com o status " + newStatus);
        }

        student.setStatus(newStatus);

        studentRepository.save(student);
    }

    private void validateSameCustomer(UUID customerOne, UUID customerTwo) {
        if (!customerOne.equals(customerTwo)) {
            throw new CustomerMismatchException("Divergência entre customer identificada entre as entidades.");
        }
    }

    protected long countStudentsInThisCustomer(UUID customerId) {
        return studentRepository.countStudentsInThisCustomer(customerId);
    }

    private long studentRegisterLimit(UUID customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        return customer.getPlan().getMaxAdministrators();
    }

    // "123.456.789-00" e "12345678900" viram o mesmo valor
    private String normalizeField(String field) {
        return field == null ? null : field.replaceAll("\\D", "");
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /*
    * processa os cursos que atendem a todas as condições p/ o aluno
     * */
    private void enrollmentCourses(Student student, UUID customerId, StudentInstitutionRequestDTO studentInstitutionRequestDTO) {
        institutionRepository.findActiveByIdAndCustomerIdIgnoringTenant(studentInstitutionRequestDTO.institutionId(), customerId)
                .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada ou inativa"));

        List<InstitutionCourse> courses  = institutionCourseRepository.findActiveByIdsAndInstitutionIgnoringTenant(studentInstitutionRequestDTO.courseIds(), studentInstitutionRequestDTO.institutionId(), customerId);

        // se algum id não encontrado: inexistente, inativo, de outra instituição ou de outro Customer
        if (courses.size() != studentInstitutionRequestDTO.courseIds().size()) {
            throw new DomainValidationException("Um ou mais cursos informados são inválidos para esta instituição");
        }

        String poolOfEnrollment = studentInstitutionRequestDTO.poolOfEnrollment() == null ? null : studentInstitutionRequestDTO.poolOfEnrollment().trim();

        if (poolOfEnrollment != null
                && !poolOfEnrollment.isEmpty()
                && studentEnrollmentRepository.existsPoolOfEnrollmentInInstitutionIgnoringTenant(studentInstitutionRequestDTO.institutionId(), poolOfEnrollment)) {
            throw new DuplicateResourceException("Este número de matrícula já está em uso nesta instituição");
        }

        courses.forEach(course -> student.addEnrollment(course, poolOfEnrollment));
    }
}
