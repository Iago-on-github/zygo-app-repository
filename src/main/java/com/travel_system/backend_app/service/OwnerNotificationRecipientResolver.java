package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/*
* utilizado para resolver as notificações do setor de documentos de saúde (admins, dono, responsável)
* */

@Component
public class OwnerNotificationRecipientResolver {

    private final DriverRepository driverRepository;
    private final StudentRepository studentRepository;

    private final UserProfileResolverService userProfileResolverService;

    public OwnerNotificationRecipientResolver(DriverRepository driverRepository, StudentRepository studentRepository, UserProfileResolverService userProfileResolverService) {
        this.driverRepository = driverRepository;
        this.studentRepository = studentRepository;
        this.userProfileResolverService = userProfileResolverService;
    }

    // usados nas notificações para mandar aos users corretos
    @Transactional(readOnly = true)
    public List<UUID> resolve(OwnerBuilt owner) {
        if (owner.type() == OwnerType.DRIVER) {
            return driverRepository.findUserAccountIdById(owner.id())
                    .map(List::of)
                    .orElse(List.of());
        }

        Student student = studentRepository.findById(owner.id()).orElse(null);
        if (student == null) {
            return List.of();
        }

        // aluno menor: notifica os responsáveis vinculados
        if (userProfileResolverService.isMinor(student.getBirthdate())) {
            return studentRepository.findResponsibleUserAccountIds(owner.id());
        }

        // aluno maior: notifica a própria conta (pode estar nula se saiu do Customer)
        return student.getUserAccount() != null ? List.of(student.getUserAccount().getId()) : List.of();
    }
}
