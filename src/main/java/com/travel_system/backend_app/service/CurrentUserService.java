package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.interfaces.ProfilePictureStrategy;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class CurrentUserService {

    private final Logger logger = LoggerFactory.getLogger(CurrentUserService.class);

    // verifica se o adm logado é o adm da plataforma
    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return auth.getAuthorities().stream().anyMatch(p -> p.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    // pega o email do user autenticado
    public static String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new AccessDeniedException("Usuário não autenticado");
        }
        return authentication.getName();
    }

}
