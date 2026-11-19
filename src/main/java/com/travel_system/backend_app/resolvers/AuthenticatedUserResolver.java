package com.travel_system.backend_app.resolvers;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.utils.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;


@Component
public class AuthenticatedUserResolver {

    private final TokenConfig tokenConfig;

    public AuthenticatedUserResolver(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    // verifica se o adm logado é o adm da plataforma
    public static boolean isPlatformAdmin() {
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

    public static Optional<UUID> getAuthenticatedUserUUID() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails.getId());
        }

        return Optional.empty();
    }

}
