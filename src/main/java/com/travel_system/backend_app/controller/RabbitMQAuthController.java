package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.UserRepository;
import com.travel_system.backend_app.service.TravelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/messaging/auth")
public class RabbitMQAuthController {
    private final TokenConfig tokenConfig;
    private final TravelService travelService;
    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(RabbitMQAuthController.class);

    public RabbitMQAuthController(TokenConfig tokenConfig, TravelService travelService, UserRepository userRepository) {
        this.tokenConfig = tokenConfig;
        this.travelService = travelService;
        this.userRepository = userRepository;
    }

    // rabbitMq authorization - valida token e libera acesso
    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateMessaging(@RequestParam("user") String usernameId, @RequestParam("password") String jwt) {
        if (!tokenConfig.validateToken(jwt)) {
            log.warn("token não é válido. {}: ", usernameId);
            return ResponseEntity.ok("deny");
        }

        String subjectFromToken = tokenConfig.getSubjectFromToken(jwt);

        UUID id = UUID.fromString(usernameId);
        boolean validateUser = userRepository.existsByEmailAndIdAndStatus(subjectFromToken, id, GeneralStatus.ACTIVE);

        if (!validateUser) {
            log.warn("Tentativa de login com ID divergente do Token! User: {}", usernameId);
            return ResponseEntity.ok("deny");
        }

        log.info("tentativa de login autorizada com sucesso {}:", usernameId);
        return ResponseEntity.ok("allow");
    }

    @PostMapping(value = "/vhost", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateVHost(@RequestParam("user") String usernameId, @RequestParam("vhost") String vhost, @RequestParam("ip") String ip) {

        if (vhost.equals("/")) {
            log.info("acesso ao vHost permitido ao user e ip: {} {}", usernameId, ip);
            return ResponseEntity.ok("allow");
        }

        log.warn("vHost negado. user={} vhost={} ip={}", usernameId, vhost, ip);
        return ResponseEntity.ok("deny");
    }

    @PostMapping(value = "/resource", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateResource(@RequestParam("user") String username,
                                                       @RequestParam("vhost") String vhost,
                                                       @RequestParam("resource") String resource,
                                                       @RequestParam("name") String name,
                                                       @RequestParam("permission") String permission) {

        // nunca permite criar ou deletar estruturas no servidor
        if (permission.equals("configure")) {
            log.warn("tentativa de configuração negada ao usuário: {}", username);
            return ResponseEntity.ok("deny");
        }

        // permite leitura de exchanges públicas
        if (permission.equals("read") || permission.equals("write")) {
            boolean isTopicType = resource.equals("topic");
            return isTopicType ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
        }

        return ResponseEntity.ok("deny");
    }

    @PostMapping(value = "/topic", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateTopic(@RequestParam("user") String username, @RequestParam("routing_key") String routingKey, @RequestParam("permission") String permission) {
        String[] routingKeyParts = routingKey.split("[/.]");
        String travelIdStr = routingKeyParts[routingKeyParts.length - 1];

        try {
            UUID travelId = UUID.fromString(travelIdStr);
            UUID studentId = UUID.fromString(username);
            
            if (permission.equals("publish")) {
                boolean isDriverLogged = travelService.isDriverLogged(username, travelId);
                return isDriverLogged ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
            }

            if (permission.equals("subscribe")) {
                boolean isStudentLogged = travelService.isStudentLogged(studentId, travelId);
                return isStudentLogged ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
            }
        } catch (Exception e) {
            log.error("Erro na autorização de tópico para o usuário {}: {}", username, e.getMessage());
            return ResponseEntity.ok("deny");
        }

        return ResponseEntity.ok("deny");
    }

}
