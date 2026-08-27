package com.travel_system.backend_app.security;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.infrastructure.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenConfig tokenConfig;

    public JwtAuthenticationFilter(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            try {
                String token = tokenConfig.resolveToken(request);
                if (token != null && tokenConfig.validateToken(token)) {
                    Authentication authentication = tokenConfig.getAuthentication(token);
                    if (authentication != null) {
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        // processa e define o tenant (seja por Token ou por impersonação)
                        resolveTenantContext(token, request);
                    }
                }
            } catch (Exception exception) {
                SecurityContextHolder.clearContext();
            }

            filterChain.doFilter(request, response);

        } finally {
            // limpeza do ThreadLocal ao final da execução da Thread
            TenantContext.removeCurrentTenant();
        }
    }

    protected void resolveTenantContext(String token, HttpServletRequest request) {
        UUID customerIdFromToken = tokenConfig.getCustomerIdFromToken(token);

        // caso seja um usuário do tipo tenant
        if (customerIdFromToken != null) {
            TenantContext.setCurrentTenant(customerIdFromToken);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isPlatformAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));

        // caso seja um administrador da plataforma
        if (isPlatformAdmin) {
            String impersonatedTenantHeader = request.getHeader("X-Customer-ID");

            if (impersonatedTenantHeader != null && !impersonatedTenantHeader.isBlank()) {
                try {
                    TenantContext.setCurrentTenant(UUID.fromString(impersonatedTenantHeader));
                } catch (IllegalArgumentException e) {
                    // se o UUID do header for inválido, limpa o contexto garantindo a consulta global sem crash
                    TenantContext.removeCurrentTenant();
                }
            }

        }

    }

    /*
    * GUIDE DOC:
    *
    * tenant = Usuário/entidade de persistência e isolada por cliente, extends de BaseTenantEntity
    * platformAdmin = Super admin restritos apenas aos devs do Zyggo
    * impersonação = quando alguém tenta de passar por outra pessoa, nesse caso, um usuário por outro. O HEADER "X-Customer-ID" cobre esse cenário
    *
    * metodo "resolveTenantContext" = realiza o processamento de verificação de Tenants
    *
    * TESTES: INSOMNIA / POSTMAN / BRUNO
    * é necessário que, em requisições para Tenants, o header contenha "X-Customer-ID".
    * para reqs de PltaformAdmin não deve ser inserido esse header
    *
    * */
}
