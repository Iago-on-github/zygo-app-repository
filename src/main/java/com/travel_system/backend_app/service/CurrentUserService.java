package com.travel_system.backend_app.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    // verifica se o adm logado é o adm da plataforma
    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return auth.getAuthorities().stream().anyMatch(p -> p.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }
}
