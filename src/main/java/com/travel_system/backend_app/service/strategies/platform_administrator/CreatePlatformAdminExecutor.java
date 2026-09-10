package com.travel_system.backend_app.service.strategies.platform_administrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.exceptions.PayloadNotFoundException;
import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.interfaces.SensitiveOperationExecutorStrategy;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.PlatformAdministrator;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.PlatformAdministratorRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CreatePlatformAdminExecutor implements SensitiveOperationExecutorStrategy {

    private final ObjectMapper objectMapper;

    private final UserAccountRepository userAccountRepository;
    private final PlatformAdministratorRepository platformAdministratorRepository;
    private final PermissionsRepository permissionsRepository;

    public CreatePlatformAdminExecutor(ObjectMapper objectMapper, UserAccountRepository userAccountRepository, PlatformAdministratorRepository platformAdministratorRepository, PermissionsRepository permissionsRepository) {
        this.objectMapper = objectMapper;
        this.userAccountRepository = userAccountRepository;
        this.platformAdministratorRepository = platformAdministratorRepository;
        this.permissionsRepository = permissionsRepository;
    }

    @Override
    public SensitiveOperationType supports() {
        return SensitiveOperationType.CREATE_PLATFORM_ADMIN;
    }

    @Transactional
    @Override
    public void execute(SensitiveOperation operation) {
        if (operation.getPayload() == null) {
            throw new PayloadNotFoundException("Payload da entidade Platform Administrator não encontrado para a operação sensível: " + operation.getId());
        }

        String payload = operation.getPayload();

        PlatformAdministratorRequestDTO platformAdministratorRequestDTO;
        try {
            platformAdministratorRequestDTO = objectMapper.readValue(payload, PlatformAdministratorRequestDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro durante a desserialziação ObjectMapper");
        }

        if (platformAdministratorRepository.existsByEmail(platformAdministratorRequestDTO.email())) {
            throw new IllegalArgumentException("Já existe um Platform ADM com o email: " + platformAdministratorRequestDTO.email());
        }

        UserAccount userAccount = new UserAccount();

        String platformAdmRole = "ROLE_PLATFORM_ADMIN";
        Permissions permissions = permissionsRepository.findByDescription(platformAdmRole)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão " + platformAdmRole + " não encontrada"));

        userAccount.setEmail(platformAdministratorRequestDTO.email());
        userAccount.setPassword(platformAdministratorRequestDTO.password()); // ja vem encoded
        userAccount.setUserAccountType(UserAccountType.PLATFORM_ADMINISTRATOR);
        userAccount.setPermissions(List.of(permissions));

        UserAccount savedUserAccount = userAccountRepository.save(userAccount);

        PlatformAdministrator platformAdministrator = new PlatformAdministrator();

        platformAdministrator.setUserAccount(savedUserAccount);

        platformAdministratorRepository.save(platformAdministrator);
    }

}

/*
* GUIDE
* com o supports faz a identificação de qual é a operação
* com o execute ele executa a operação em si
* */
