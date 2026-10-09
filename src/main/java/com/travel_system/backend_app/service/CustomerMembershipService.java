package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.interfaces.LeaveCustomerStrategy;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.security.AuthTokens;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.token.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class CustomerMembershipService {

    private final List<LeaveCustomerStrategy> strategies;

    private final UserAccountRepository userAccountRepository;

    private final TokenConfig tokenConfig;
    private final SetupAuthenticationService setupAuthenticationService;

    public CustomerMembershipService(List<LeaveCustomerStrategy> strategies, UserAccountRepository userAccountRepository, TokenConfig tokenConfig, SetupAuthenticationService setupAuthenticationService) {
        this.strategies = strategies;
        this.userAccountRepository = userAccountRepository;
        this.tokenConfig = tokenConfig;
        this.setupAuthenticationService = setupAuthenticationService;
    }

    @Transactional
    public AuthTokens leaveCustomer() {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        UserAccount userAccount = userAccountRepository.findByEmailForUpdate(authenticatedUserEmail)
                .orElseThrow(() -> new AccessDeniedException("Usuário não autenticado"));

        // busca pelo tipo da conta a ser removida do customer
        // qualquer tipo da entidade de domínio é aceito, menos UNASSIGNED
        LeaveCustomerStrategy strategy  = strategies.stream()
                .filter(s -> s.supports(userAccount.getUserAccountType()))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("Operação indisponível para este tipo de conta"));

        strategy.validateLeave(userAccount);

        // pede a senha novamente
        setupAuthenticationService.consumeSensitiveOperationPermission(authenticatedUserEmail, SensitiveOperationType.LEAVE_CUSTOMER);

        // realiza a remoção dos dados do driver preservando nome e alguns outros p/ histórico
        strategy.unlinkFromCustomer(userAccount);

        userAccount.setUserAccountType(UserAccountType.UNASSIGNED);
        userAccount.getPermissions().clear();

        // gera novamente os tokens com base nas novas permissões (sem customerId)
        return tokenConfig.generateBothTokens(userAccount, null);
    }
}
