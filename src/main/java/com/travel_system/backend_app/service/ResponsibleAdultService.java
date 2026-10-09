package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.ResponsibleAdultRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.ResponsibleAdultResponseMapper;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.invitation.responsible.ResponsibleAdultProfileDTO;
import com.travel_system.backend_app.model.dtos.request.CpfSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.ResponsibleAdultResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponsibleAdultDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.ResponsibleAdultConstants.MAX_STUDENTS_PER_RESPONSIBLE_ADULT;
import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class ResponsibleAdultService {
    private final Logger log = LoggerFactory.getLogger(ResponsibleAdultService.class);

    private final ResponsibleAdultRepository responsibleAdultRepository;
    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;

    private final PasswordEncoder passwordEncoder;

    private final ResponsibleAdultRequestMapper responsibleAdultRequestMapper;
    private final ResponsibleAdultResponseMapper responsibleAdultResponseMapper;

    public ResponsibleAdultService(ResponsibleAdultRepository responsibleAdultRepository, UserAccountRepository userAccountRepository, StudentRepository studentRepository, PasswordEncoder passwordEncoder, ResponsibleAdultRequestMapper responsibleAdultRequestMapper, ResponsibleAdultResponseMapper responsibleAdultResponseMapper) {
        this.responsibleAdultRepository = responsibleAdultRepository;
        this.userAccountRepository = userAccountRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.responsibleAdultRequestMapper = responsibleAdultRequestMapper;
        this.responsibleAdultResponseMapper = responsibleAdultResponseMapper;
    }

    @Transactional(readOnly = true)
    public Page<ResponsibleAdultResponseDTO> getAllResponsibleAdults(String email, String name, String lastName, String neighborhood, Pageable pageable) {
        UUID customerId = getCurrentTenant();
        if (customerId == null) {
            throw new AccessDeniedException("Usuário sem Customer vinculado");
        }

        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return responsibleAdultRepository.findAllByOptionalParameters(
                customerId,
                        blankToNull(normalizedEmail),
                        blankToNull(name),
                        blankToNull(lastName),
                        blankToNull(neighborhood),
                        pageOnly)
                .map(responsibleAdultResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public ResponsibleAdultResponseDTO getResponsibleAdultById(UUID responsibleAdultId) {
        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findById(responsibleAdultId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo Id: " + responsibleAdultId));

        return responsibleAdultResponseMapper.toDTO(responsibleAdult);
    }

    @Transactional(readOnly = true)
    public Page<ResponsibleAdultResponseDTO> getResponsibleAdultByName(String responsibleAdultName, Pageable pageable) {
        return responsibleAdultRepository.findByName(responsibleAdultName, pageable).map(responsibleAdultResponseMapper::toDTO);

    }

    @Transactional(readOnly = true)
    public ResponsibleAdultResponseDTO getResponsibleAdultByCpf(CpfSearchRequestDTO dto) {
        String normalizeCpf = dto.cpf() == null ? null : dto.cpf().replaceAll("\\D", "");

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByCpf(normalizeCpf)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo Cpf: " + normalizeCpf));

        return responsibleAdultResponseMapper.toDTO(responsibleAdult);
    }

    @Transactional(readOnly = true)
    public ResponsibleAdultResponseDTO getResponsibleAdultByStudentId(UUID studentId) {
        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByStudentsId(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo Id do estudante: " + studentId));

        return responsibleAdultResponseMapper.toDTO(responsibleAdult);
    }

    @Transactional(readOnly = true)
    public Set<StudentResponsibleAdultDTO> getStudentsByResponsibleAdult(UUID responsibleAdultId) {
        return responsibleAdultRepository.findStudentsById(responsibleAdultId);
    }

    @Transactional(readOnly = true)
    public ResponsibleAdultResponseDTO getCurrentResponsibleAdult() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum Responsável encontrado pelo Email: " + authenticatedUserEmail));

        return responsibleAdultResponseMapper.toDTO(responsibleAdult);
    }

    public ResponsibleAdult createForExistingAccount(UserAccount userAccount, UUID customerId, ResponsibleAdultProfileDTO profileDTO) {

        if (responsibleAdultRepository.existsByUserAccountIdIgnoringTenant(userAccount.getId())) {
            throw new DuplicateResourceException("Esse user já existe no sistema");
        }

        // validação de email (locais), telefone e cpf (globais)
        if (responsibleAdultRepository.existsByEmail(userAccount.getEmail())) {
            throw new DuplicateResourceException("Esse email já existe no sistema");
        }

        if (responsibleAdultRepository.existsByTelephoneIgnoringTenant(profileDTO.responsibleAdultAccept().telephone())) {
            throw new DuplicateResourceException("Esse telefone já existe no sistema");
        }

        if (responsibleAdultRepository.existsByCpfIgnoringTenant(profileDTO.responsibleAdultAccept().cpf())) {
            throw new DuplicateResourceException("Esse cpf já existe no sistema");
        }

        // validação para idade
        int responsibleYears = Period.between(profileDTO.responsibleAdultAccept().birthdate(), LocalDate.now()).getYears();

        if (responsibleYears < 18) {
            throw new UnderageResponsibleAdultException("O responsável deve ter 18 anos ou mais");
        }

        ResponsibleAdult responsibleAdult = responsibleAdultRequestMapper.toEntity(profileDTO);

        responsibleAdult.setUserAccount(userAccount);
        responsibleAdult.assignCustomer(customerId);
        responsibleAdult.setStatus(GeneralStatus.ACTIVE);

        return responsibleAdultRepository.save(responsibleAdult);
    }

    @Transactional
    public ResponsibleAdultResponseDTO updateResponsibleAdult(ResponsibleAdultUpdateDTO dto) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + authenticatedUserEmail));

        // validações de duplicação
        if (dto.email() != null && !dto.email().isBlank()) {
            if (!dto.email().equals(responsibleAdult.getUserAccount().getEmail())) {
                if (userAccountRepository.existsByEmail(dto.email())) {
                    throw new DuplicateResourceException("Já existe um responsável com o email: " + dto.email());
                }
            }
        }

        // cpf
        if (dto.cpf() != null && !dto.cpf().isBlank()) {
            if (!dto.cpf().equals(responsibleAdult.getCpf())) {
                if (responsibleAdultRepository.existsByCpfIgnoringTenant(dto.cpf())) {
                    throw new DuplicateResourceException("Já existe um responsável com o CPF: " + dto.cpf());
                }
            }
        }

        // telephone
        if (dto.telephone() != null && !dto.telephone().isBlank()) {
            if (!dto.telephone().equals(responsibleAdult.getTelephone())) {
                if (responsibleAdultRepository.existsByTelephoneIgnoringTenant(dto.telephone())) {
                    throw new DuplicateResourceException("Já existe um responsável com o Telefone: " + dto.telephone());
                }
            }
        }

        // validação da troca da data de aniversário
        if (dto.birthdate() != null) {
            int responsibleYears = Period.between(dto.birthdate(), LocalDate.now()).getYears();

            if (responsibleYears < 18) {
                throw new UnderageResponsibleAdultException("O responsável deve ter 18 anos ou mais");
            }
        }

        UserAccount userAccount = responsibleAdult.getUserAccount();

        ResponsibleAdult responsibleAdultUpdated = responsibleAdultRequestMapper.toUpdate(dto, responsibleAdult);

        // atualiza senha
        if (dto.password() != null && !dto.password().isBlank()) {
            userAccount.setPassword(passwordEncoder.encode(dto.password()));
        }

        responsibleAdultRepository.save(responsibleAdultUpdated);

        return responsibleAdultResponseMapper.toDTO(responsibleAdultUpdated);
    }

    @Transactional
    public void updateResponsibleAdultStatus(UpdateStatusDTO dto) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + authenticatedUserEmail));

        if (responsibleAdult.getStatus() == dto.status()) {
            throw new DuplicateResourceException("ResponsibleAdult já está com status, " + dto);
        }

        // caso haja estudantes menores de idade vinculados não deixa desativar
        if (!responsibleAdult.getStudents().isEmpty()) {
            throw new MinorStudentResponsibleAdultTransferRequiredException("Existem estudantes menores de idade vinculados a essa conta. É necessário atribuir um novo responsável para eles para continuar com a desativação");
        }

        responsibleAdult.setStatus(dto.status());

        responsibleAdultRepository.save(responsibleAdult);
    }

    /*
     * vincular estudantes, aqui deixar a responsabildade com o responsável pelo vínculo mesmo
     * o estudante (menor) pode criar uma conta sem um responsável, no entanto, ele não poderá participar de nenhuma atividade do sistema
     * */
    @Transactional
    public void addStudentsToResponsibleAdult(StudentResponsibleAdultDTO dto) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        UUID studentId = dto.studentId();
        StudentRelationshipType studentRelationshipType = dto.studentRelationshipType();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + authenticatedUserEmail));

        if (responsibleAdult.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("O responsável autenticado está desativado no sistema");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado pelo ID: " + studentId));

        if (student.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("O estudante informado está desativado no sistema");
        }

        // valida mesmo customer
        validateSameCustomer(student.getCustomerId(), responsibleAdult.getCustomerId());

        ResponsibleAdult responsibleAdultByStudent = student.getResponsibleAdult();

        // verifica se o estudante já não está vinculado com um responsável
        if (responsibleAdultByStudent != null) {
            throw new StudentAlreadyHasResponsibleAdultException("O estudante ja possui um responsável vínculado");
        }

        long currentStudents = studentRepository.countByResponsibleAdultId(responsibleAdult.getId());

        // verifica quantidade máxima de alunos
        if (currentStudents >= MAX_STUDENTS_PER_RESPONSIBLE_ADULT) {
            throw new ResponsibleAdultStudentLimitExceededException("O responsável atingiu a quantidade máxima permitida de estudantes");
        }

        responsibleAdult.addStudent(student);
        student.setStudentRelationshipType(studentRelationshipType);

        responsibleAdultRepository.save(responsibleAdult);
    }

    /*
    * trsferir estudantes para outro responsável
    * remove o estudante a ser transferido do responsável atual e adc no outro
    * importante pois estudantes menores de idade não podem ser desvinculados e deixados sem um responsável
    * */
    @Transactional
    public void transferStudentToAnotherResponsible(UUID newResponsibleAdultId, UUID studentIdToTransfer) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        ResponsibleAdult currentResponsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                        .orElseThrow(() -> new EntityNotFoundException("ResponsibleAdult não encontrado para o usuário autenticado"));

        if (currentResponsibleAdult.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("O responsável autenticado está desativado no sistema");
        }

        // busca o estudante para ser transferido
        Student student = studentRepository.findById(studentIdToTransfer)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Student não encontrado pelo ID: " + studentIdToTransfer));

        ResponsibleAdult studentCurrentResponsibleAdult = student.getResponsibleAdult();

        // verifica se o student está vinculado ao responsável autenticiado
        if (studentCurrentResponsibleAdult == null || !studentCurrentResponsibleAdult.getId().equals(currentResponsibleAdult.getId())) {
            throw new StudentNotAssociatedWithResponsibleAdultException("O estudante informado não está vinculado ao responsável autenticado");
        }

        int studentAge = Period.between(student.getBirthdate(), LocalDate.now()).getYears();

        // caso o estudante seja menor, necessário vincular um outro responsável a ele
        if (studentAge < 18) {
            log.info("Estudante menor de idade, necessário vincular um novo responsável.");

            ResponsibleAdult newResponsibleAdult = responsibleAdultRepository.findById(newResponsibleAdultId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "ResponsibleAdult não encontrado pelo ID: " + newResponsibleAdultId));

            if (newResponsibleAdult.getStatus() == GeneralStatus.INACTIVE) {
                throw new InactiveAccountException("O novo responsável está desativado no sistema");
            }

            if (currentResponsibleAdult.getId().equals(newResponsibleAdult.getId())) {
                throw new CannotTransferStudentToSameResponsibleAdultException("Não é possível transferir o estudante para o mesmo responsável");
            }

            long currentStudents = studentRepository.countByResponsibleAdultId(newResponsibleAdult.getId());

            // verifica quantidade máxima de alunos
            if (currentStudents >= MAX_STUDENTS_PER_RESPONSIBLE_ADULT) {
                throw new ResponsibleAdultStudentLimitExceededException("O responsável atingiu a quantidade máxima permitida de estudantes");
            }
            // adciona o novo
            newResponsibleAdult.addStudent(student);
        }

        // remove do atual
        currentResponsibleAdult.removeStudent(student);
    }

    // desvincular estudantes = mesma lógica acima caso seja menor
    @Transactional
    public void removeStudent(UUID studentId) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + authenticatedUserEmail));

        if (responsibleAdult.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("O responsável autenticado está desativado no sistema");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado pelo ID: " + studentId));

        // valida mesmo customer
        validateSameCustomer(student.getCustomerId(), responsibleAdult.getCustomerId());

        int studentAge = Period.between(student.getBirthdate(), LocalDate.now()).getYears();

        if (studentAge < 18) {
            throw new MinorStudentResponsibleAdultTransferRequiredException("Não é possível seguir com a operação, é necessário vincular um novo responsável ao estudante menor de idade.");
        }

        ResponsibleAdult responsibleAdultByStudent = student.getResponsibleAdult();

        // verifica se o estudante não está vinculado com o responsável
        if (responsibleAdultByStudent != null && !responsibleAdultByStudent.getId().equals(responsibleAdult.getId())) {
            throw new StudentNotAssociatedWithResponsibleAdultException("O estudante informado não está vinculado ao responsável autenticado");
        }

        responsibleAdult.removeStudent(student);

        responsibleAdultRepository.save(responsibleAdult);
    }

    private void validateSameCustomer(UUID customerOne, UUID customerTwo) {
        if (!customerOne.equals(customerTwo)) {
            throw new CustomerMismatchException("Divergência entre customer identificada entre as entidades.");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
