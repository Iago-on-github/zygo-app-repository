package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TenantConfig;
import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.controller.RabbitMQAuthController;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RabbitMQAuthService {
    private final TokenConfig tokenConfig;
    private final TravelService travelService;
    private final UserRepository userRepository;

    @Value("${rabbitmq_user}")
    private String rabbitmq_user;
    @Value("${rabbitmq_password}")
    private String rabbitmq_password;

    private final Logger log = LoggerFactory.getLogger(RabbitMQAuthService.class);

    public RabbitMQAuthService(TokenConfig tokenConfig, TravelService travelService, UserRepository userRepository) {
        this.tokenConfig = tokenConfig;
        this.travelService = travelService;
        this.userRepository = userRepository;
    }

    // rabbitMq authorization - valida token e libera acesso
    public boolean authenticateMessaging(String username, String password) {
        // verifica se é o próprio sistema tentando autenticar
        if (username.equals(rabbitmq_user) && password.equals(rabbitmq_password)) {
            log.info("[authenticateMessaging] Backend do sistema autorizado com sucesso.");
            return true;
        }

        // validação de users (student/driver)
        try {
            if (!tokenConfig.validateToken(password)) {
                log.warn("[authenticateMessaging] Token inválido para o usuário.");
                return false;
            }

            String subjectFromToken = tokenConfig.getSubjectFromToken(password);
            UUID id = UUID.fromString(username);

            // recupera o CustomerID do token e injeta na Thread atual
            UUID customerIdFromToken = tokenConfig.getCustomerIdFromToken(password);
            if (customerIdFromToken != null) {
                TenantContext.setCurrentTenant(customerIdFromToken);
            }


            boolean validateUser = userRepository.existsByEmailAndIdAndStatus(subjectFromToken, id, GeneralStatus.ACTIVE);

            if (!validateUser) {
                log.warn("[authenticateMessaging] ID divergente do Token ou usuário inativo.");
                return false;
            }

            log.info("[authenticateMessaging] Login de usuário autorizado.");
            return true;

        } catch (Exception e) {
            log.error("[authenticateMessaging] Erro ao processar autenticação de mensageria: {}", e.getMessage());
            return false;
        } finally {
            TenantContext.removeCurrentTenant();
        }
    }

    public boolean authenticateVHost(String usernameId, String vhost, String ip) {
        if (vhost.equals("/")) {
            log.info("acesso ao vHost permitido ao ip: {}", ip);
            return true;
        }

        log.warn("vHost negado. vhost={} ip={}", vhost, ip);
        return false;
    }

    public boolean authenticateResource(String username, String vhost, String resource, String name, String permission) {
        // nunca permite criar ou deletar estruturas no servidor
        if (permission.equals("configure")) {
            log.warn("tentativa de configuração do servidor negada.");
            return false;
        }

        // permite leitura de exchanges públicas
        if (permission.equals("read") || permission.equals("write")) {
            log.info("resource={}, permission={}, name={}", resource, permission, name);
            return resource.equals("topic");
        }

        return false;
    }

    public boolean authenticateTopic(String usernameId, String routingKey, String permission) {
        Matcher matcher = TOPIC_PATTERN.matcher(routingKey);

        // valida a estrutura da routingKey recebida
        if (!matcher.matches()) {
            log.warn("[authenticateTopic] Estrutura de tópico inválida: {}", routingKey);
            return false;
        }

        String costumerIdStr = matcher.group(1);
        String travelIdStr = matcher.group(2);

        try {
            UUID customerId = UUID.fromString(costumerIdStr);
            UUID travelId = UUID.fromString(travelIdStr);
            UUID userId = UUID.fromString(usernameId);

            // injeta o customerId do tópico na thread atual
            TenantContext.setCurrentTenant(customerId);

            // valida as permissões já com o hibernate aplicando o tenant nas consultas
            if ("publish".equalsIgnoreCase(permission)) {
                log.info("[authenticateTopic] Validando publicação do Motorista {} na Viagem {}", userId, travelId);
                return travelService.isDriverLogged(usernameId, travelId);
            }

            if ("subscribe".equalsIgnoreCase(permission)) {
                log.info("[authenticateTopic] Validando inscrição do Estudante {} na Viagem {}", userId, travelId);
                return travelService.isStudentLogged(userId, travelId);
            }
        } catch (IllegalArgumentException e) {
            log.error("[authenticateTopic] Erro ao converter UUIDs do tópico ou usuário: {}", e.getMessage());
            return false;
        } finally {
            TenantContext.removeCurrentTenant();
        }

        return false;
    }

    // aceita tanto separadores de barra '/' quanto de ponto '.'
    private static final Pattern TOPIC_PATTERN = Pattern.compile(
            "^tenants[/.]" +
                    "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})" +
                    "[/.]travels[/.]" +
                    "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})" +
                    "[/.]location$"
    );
}
