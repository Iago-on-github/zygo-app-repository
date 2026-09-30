package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.TargetUserType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PermissionsService {

    private final UserAccountRepository userAccountRepository;
    private final PermissionsRepository permissionsRepository;

    public PermissionsService(UserAccountRepository userAccountRepository, PermissionsRepository permissionsRepository) {
        this.userAccountRepository = userAccountRepository;
        this.permissionsRepository = permissionsRepository;
    }

    @Transactional
    public void assignPermissions(UserAccount expectedUserAccount, TargetUserType expectedType) {
        String permission = resolveUserType(expectedType);

        Permissions perm = permissionsRepository.findByDescription(permission)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão não encontrada"));

        expectedUserAccount.getPermissions().add(perm);

        userAccountRepository.save(expectedUserAccount);
    }

    private String resolveUserType(TargetUserType targetUserType) {
        UserAccountType userAccountType = targetUserType.getUserAccountType();

        return switch (userAccountType) {
            case STUDENT -> userAccountType.STUDENT_PERMISSION_ROLE;
            case DRIVER -> userAccountType.DRIVER_PERMISSION_ROLE;
            case ADMINISTRATOR -> userAccountType.ADMINISTRATOR_PERMISSION_ROLE;
            case PLATFORM_ADMINISTRATOR -> userAccountType.PLATFORM_ADMINISTRATOR_PERMISSION_ROLE;
            case RESPONSIBLE_ADULT -> userAccountType.RESPONSIBLE_ADULT_PERMISSION_ROLE;
            case UNASSIGNED -> throw new IllegalArgumentException("UNASSIGNED não possui uma permissão no sistema");
        };
    }

}
