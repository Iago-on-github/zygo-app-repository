package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.controller.RabbitMQAuthController;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

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
            log.info("[authenticateMessaging] Backend do sistema autorizado com sucesso: {}", username);
            return true;
        }

        // validação de users (student/driver)
        try {
            if (!tokenConfig.validateToken(password)) {
                log.warn("[authenticateMessaging] Token inválido para o usuário: {}", username);
                return false;
            }

            String subjectFromToken = tokenConfig.getSubjectFromToken(password);
            UUID id = UUID.fromString(username);

            boolean validateUser = userRepository.existsByEmailAndIdAndStatus(subjectFromToken, id, GeneralStatus.ACTIVE);

            if (!validateUser) {
                log.warn("[authenticateMessaging] ID divergente do Token ou usuário inativo! User: {}", username);
                return false;
            }

            log.info("[authenticateMessaging] Login de usuário autorizado: {}", username);
            return true;

        } catch (Exception e) {
            log.error("[authenticateMessaging] Erro ao processar autenticação de mensageria: {}", e.getMessage());
            return false;
        }
    }

    public boolean authenticateVHost(String usernameId, String vhost, String ip) {
        if (vhost.equals("/")) {
            log.info("acesso ao vHost permitido ao user e ip: {} {}", usernameId, ip);
            return true;
        }

        log.warn("vHost negado. user={} vhost={} ip={}", usernameId, vhost, ip);
        return false;
    }

    public boolean authenticateResource(String username, String vhost, String resource, String name, String permission) {
        // nunca permite criar ou deletar estruturas no servidor
        if (permission.equals("configure")) {
            log.warn("tentativa de configuração negada ao usuário: {}", username);
            return false;
        }

        // permite leitura de exchanges públicas
        if (permission.equals("read") || permission.equals("write")) {
            return resource.equals("topic");
        }

        return false;
    }

    public boolean authenticateTopic(String username, String routingKey, String permission) {
        String[] routingKeyParts = routingKey.split("[/.]");
        String travelIdStr = routingKeyParts[routingKeyParts.length - 1];

        try {
            UUID travelId = UUID.fromString(travelIdStr);
            UUID studentId = UUID.fromString(username);

            if (permission.equals("publish")) {
                return travelService.isDriverLogged(username, travelId);
            }

            if (permission.equals("subscribe")) {
                return travelService.isStudentLogged(studentId, travelId);
            }
        } catch (Exception e) {
            log.error("Erro na autorização de tópico para o usuário {}: {}", username, e.getMessage());
            return false;
        }

        return false;
    }
}
