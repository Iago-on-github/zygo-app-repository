package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.PermissionNotFoundException;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PermissionsService {

    private final UserAccountRepository userAccountRepository;
    private final PermissionsRepository permissionsRepository;

    public PermissionsService(UserAccountRepository userAccountRepository, PermissionsRepository permissionsRepository) {
        this.userAccountRepository = userAccountRepository;
        this.permissionsRepository = permissionsRepository;
    }

    public void assignPermissions(UUID id, String permission) {
        UserAccount expectedUser = userAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado " + id));

        Permissions perm = permissionsRepository.findByDescription(permission)
                .orElseThrow(() -> new PermissionNotFoundException("Permissão não encontrada"));

        expectedUser.getPermissions().add(perm);
        userAccountRepository.save(expectedUser);
    }

}
