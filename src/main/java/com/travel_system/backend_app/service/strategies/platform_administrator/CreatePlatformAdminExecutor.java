package com.travel_system.backend_app.service.strategies.platform_administrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.exceptions.PayloadNotFoundException;
import com.travel_system.backend_app.interfaces.SensitiveOperationExecutorStrategy;
import com.travel_system.backend_app.model.PlatformAdministrator;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorCreationPayload;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.PlatformAdministratorRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.PermissionsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreatePlatformAdminExecutor implements SensitiveOperationExecutorStrategy {

    private final UserAccountRepository userAccountRepository;
    private final PlatformAdministratorRepository platformAdministratorRepository;

    private final PermissionsService permissionsService;

    private final ObjectMapper objectMapper;

    public CreatePlatformAdminExecutor(UserAccountRepository userAccountRepository, PlatformAdministratorRepository platformAdministratorRepository, PermissionsService permissionsService, ObjectMapper objectMapper) {
        this.userAccountRepository = userAccountRepository;
        this.platformAdministratorRepository = platformAdministratorRepository;
        this.permissionsService = permissionsService;
        this.objectMapper = objectMapper;
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

        PlatformAdministratorCreationPayload platformAdministratorCreationPayload;
        try {
            platformAdministratorCreationPayload = objectMapper.readValue(payload, PlatformAdministratorCreationPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro durante a desserialziação ObjectMapper");
        }

        if (platformAdministratorRepository.existsByEmail(platformAdministratorCreationPayload.email())) {
            throw new IllegalArgumentException("Já existe um Platform ADM com o email: " + platformAdministratorCreationPayload.email());
        }

        UserAccount userAccount = new UserAccount();

        userAccount.setEmail(platformAdministratorCreationPayload.email());
        userAccount.setPassword(platformAdministratorCreationPayload.hashPassword()); // ja vem encoded
        userAccount.setUserAccountType(UserAccountType.PLATFORM_ADMINISTRATOR);
        permissionsService.assignPermissions(userAccount, TargetUserType.PLATFORM_ADMINISTRATOR);

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
