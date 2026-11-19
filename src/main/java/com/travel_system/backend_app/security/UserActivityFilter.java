package com.travel_system.backend_app.security;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.service.UserActivityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// não pode ser bean do spring para não rodar duas vezes, ele roda no addFilterAfter so s. security
public class UserActivityFilter extends OncePerRequestFilter {
    private final Logger log = LoggerFactory.getLogger(UserActivityFilter.class);

    private final TokenConfig tokenConfig;
    private final UserActivityService userActivityService;

    public UserActivityFilter(TokenConfig tokenConfig, UserActivityService userActivityService) {
        this.tokenConfig = tokenConfig;
        this.userActivityService = userActivityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            // não seta autenticação, o filtro principal já faz isso
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // se houver user autenticado, extrai o id do token
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                String token = tokenConfig.resolveToken(request);

                if (token != null && tokenConfig.validateToken(token)) {
                    UUID idFromToken = tokenConfig.getIdFromToken(token);

                    userActivityService.recordActivity(idFromToken);
                }
            }
        } catch (Exception e) {
            // so registra log e a auth segue normalmente
            log.warn("[UserActivityFilter] - ocorreu um erro durante o processo de registro de ativdade na fase de auth, cause: ", e.getCause());
        }

        filterChain.doFilter(request, response);
    }

    /*
    * faz com que o spring pule esses filtros nesta requisição
    * */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/v1/auth/refresh") || path.startsWith("/v1/messaging/auth");
    }
}
