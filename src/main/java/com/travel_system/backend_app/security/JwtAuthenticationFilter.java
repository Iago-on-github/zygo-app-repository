package com.travel_system.backend_app.security;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.CurrentUserService;
import com.travel_system.backend_app.service.UserProfileResolverService;
import jakarta.persistence.EntityNotFoundException;
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
    private final CurrentUserService currentUserService;
    private final UserProfileResolverService userProfileResolverService;

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(TokenConfig tokenConfig, CurrentUserService currentUserService, UserProfileResolverService userProfileResolverService, CustomerRepository customerRepository, UserAccountRepository userAccountRepository) {
        this.tokenConfig = tokenConfig;
        this.currentUserService = currentUserService;
        this.userProfileResolverService = userProfileResolverService;
        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
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

                        System.out.println("processing first doFilterInternal");

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

        System.out.println("processing resolveTenantContext");

        boolean isPlatformAdmin = currentUserService.isPlatformAdmin();

        // platformAdmin atuando em um Customer específico
        if (isPlatformAdmin) {
            String impersonatedTenantHeader = request.getHeader("X-Customer-ID");

            if (impersonatedTenantHeader != null && !impersonatedTenantHeader.isBlank()) {
                try {
                    UUID customerId = UUID.fromString(impersonatedTenantHeader);

                    if (!customerRepository.existsById(customerId)) {
                        throw new EntityNotFoundException(
                                "Customer provido do header não existe"
                        );
                    }

                    TenantContext.setCurrentTenant(customerId);
                    return;

                } catch (IllegalArgumentException e) {
                    throw new DomainValidationException(
                            "Header 'X-Customer-ID' inválido"
                    );
                }
            }

            // platformAdmin sem impersonação permanece sem tenant
            return;
        }

        /*
         * Usuário comum:
         * o JWT identifica a UserAccount, mas não é a fonte do customerId
         */
        String email = tokenConfig.getAuthentication(token).getName();

        UserAccount userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null) {
            throw new EntityNotFoundException("UserAccount não encontrada");
        }

        UUID customerId = userProfileResolverService.resolveCustomerId(userAccount);

        System.out.println("customerId from resolveTenantContext: " + customerId);

        /*
         * UNASSIGNED: ainda não possui perfil/Customer.
         */
        if (customerId == null) {
            TenantContext.removeCurrentTenant();
            return;
        }

        TenantContext.setCurrentTenant(customerId);
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
