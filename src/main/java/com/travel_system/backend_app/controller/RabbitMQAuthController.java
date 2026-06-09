package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.service.RabbitMQAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/v1/messaging/auth")
public class RabbitMQAuthController {
    private final RabbitMQAuthService rabbitMQAuthService;

    public RabbitMQAuthController(RabbitMQAuthService rabbitMQAuthService) {
        this.rabbitMQAuthService = rabbitMQAuthService;
    }

    @Operation(
            summary = "[RabbitMQ] Autenticação Primária de Conexão",
            description = "### ATENÇÃO: Rota utilizada exclusivamente pelo servidor RabbitMQ ###\n\n" +
                    "Este endpoint **não deve ser chamado diretamente pelo cliente (front-end)**. Ele é invocado internamente pelo broker RabbitMQ (via plugin HTTP Auth) para validar as credenciais quando o aplicativo tenta abrir uma conexão MQTT.\n\n" +
                    "#### Como o Front-end deve se conectar ao RabbitMQ:\n" +
                    "- **Username:** O front-end deve passar o seu próprio **ID de Usuário (UUID)** (ex: do estudante ou do motorista).\n" +
                    "- **Password:** O front-end deve passar o **Token JWT de acesso** obtido no login.\n\n" +
                    "O backend validará a assinatura do Token, extrairá o e-mail e confirmará se o ID enviado corresponde ao dono do Token e se a conta está ativa.",
            tags = {"RabbitMQ Internal Auth"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "O RabbitMQ interpretará o corpo textual da resposta para decidir o acesso:\n" +
                            "- `allow`: Credenciais válidas. Conexão autorizada.\n" +
                            "- `deny`: Token inválido, expirado, ID divergente ou usuário inativo. Conexão recusada.",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "allow")
                    )
            )
    })
    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateMessaging(
            @Parameter(description = "ID do usuário (UUID) enviado no campo username da conexão") @RequestParam("user") String username,
            @Parameter(description = "Token JWT de acesso limpo enviado no campo password da conexão") @RequestParam("password") String password) {
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
    public ResponseEntity<String> authenticateTopic(@RequestParam("authenticatedUserId") String usernameId, @RequestParam("routing_key") String routingKey, @RequestParam("permission") String permission) {
        boolean isTopicAuth = rabbitMQAuthService.authenticateTopic(usernameId, routingKey, permission);

        return isTopicAuth ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

}
