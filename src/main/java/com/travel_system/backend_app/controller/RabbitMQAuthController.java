package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.UserRepository;
import com.travel_system.backend_app.service.RabbitMQAuthService;
import com.travel_system.backend_app.service.TravelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/messaging/auth")
public class RabbitMQAuthController {
    private final RabbitMQAuthService rabbitMQAuthService;

    public RabbitMQAuthController(RabbitMQAuthService rabbitMQAuthService) {
        this.rabbitMQAuthService = rabbitMQAuthService;
    }

    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateMessaging(@RequestParam("user") String username, @RequestParam("password") String password) {
        boolean isAuthorized = rabbitMQAuthService.authenticateMessaging(username, password);

        return isAuthorized ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @PostMapping(value = "/vhost", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateVHost(@RequestParam("user") String usernameId, @RequestParam("vhost") String vhost, @RequestParam("ip") String ip) {
        boolean verifyVHostAccess = rabbitMQAuthService.authenticateVHost(usernameId, vhost, ip);

        return verifyVHostAccess ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @PostMapping(value = "/resource", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateResource(@RequestParam("user") String username, @RequestParam("vhost") String vhost, @RequestParam("resource") String resource, @RequestParam("name") String name, @RequestParam("permission") String permission) {
        boolean verifyResourcePermissions = rabbitMQAuthService.authenticateResource(username, vhost, resource, name, permission);

        return verifyResourcePermissions ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @PostMapping(value = "/topic", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateTopic(@RequestParam("user") String username, @RequestParam("routing_key") String routingKey, @RequestParam("permission") String permission) {
        boolean isTopicAuth = rabbitMQAuthService.authenticateTopic(username, routingKey, permission);

        return isTopicAuth ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

}
