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
            @ApiResponse(responseCode = "200", description = "O RabbitMQ interpretará o corpo textual da resposta para decidir o acesso:\n" +
                            "- `allow`: Credenciais válidas. Conexão autorizada.\n" +
                            "- `deny`: Token inválido, expirado, ID divergente ou usuário inativo. Conexão recusada.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "allow")))
    })
    @PostMapping(value = "/user", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateMessaging(
            @Parameter(description = "ID do usuário (UUID) enviado no campo username da conexão") @RequestParam("user") String username,
            @Parameter(description = "Token JWT de acesso limpo enviado no campo password da conexão") @RequestParam("password") String password) {
        boolean isAuthorized = rabbitMQAuthService.authenticateMessaging(username, password);

        return isAuthorized ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @Operation(
            summary = "[RabbitMQ] Autorização de Acesso ao Virtual Host",
            description = "### ATENÇÃO: Rota utilizada exclusivamente pelo servidor RabbitMQ ###\n\n" +
                    "Este endpoint **não deve ser chamado diretamente pelo cliente (front-end)**. Ele é invocado internamente pelo broker RabbitMQ logo após a autenticação do usuário para validar se ele possui permissão de entrada no Virtual Host (vHost) solicitado.\n\n" +
                    "#### Regra de Negócio:\n" +
                    "- O sistema está travado para aceitar **apenas o vHost padrão (`/`)**.\n" +
                    "- Qualquer tentativa de conexão utilizando um vHost customizado ou diferente será barrada imediatamente pelo Broker (resposta `deny`).\n" +
                    "- **Nota para o Front-end:** Certifique-se de que a biblioteca de WebSocket/MQTT do cliente esteja configurada para apontar para o vHost padrão `/`.",
            tags = {"RabbitMQ Internal Auth"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "O RabbitMQ interpretará o corpo textual da resposta para decidir o acesso:\n" +
                            "- `allow`: O vHost solicitado é o padrão (`/`). Acesso permitido.\n" +
                            "- `deny`: Tentativa de acesso a um vHost inválido ou inexistente. Acesso recusado.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "allow")
                    )
            )
    })
    @PostMapping(value = "/vhost", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateVHost(@Parameter(description = "ID do usuário (UUID) que está tentando a conexão") @RequestParam("user") String usernameId,
                                                    @Parameter(description = "O Virtual Host alvo da conexão (Esperado: '/')") @RequestParam("vhost") String vhost,
                                                    @Parameter(description = "Endereço IP de origem do cliente que está se conectando") @RequestParam("ip") String ip) {
        boolean verifyVHostAccess = rabbitMQAuthService.authenticateVHost(usernameId, vhost, ip);

        return verifyVHostAccess ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @Operation(
            summary = "[RabbitMQ] Autorização de Operações em Recursos",
            description = "### ATENÇÃO: Rota utilizada exclusivamente pelo servidor RabbitMQ ###\n\n" +
                    "Este endpoint **não deve ser chamado diretamente pelo cliente (front-end)**. Ele é invocado internamente pelo broker RabbitMQ para verificar se o usuário tem permissão para executar uma ação específica (ler, escrever ou configurar) em um recurso (Exchange ou Fila) do servidor.\n\n" +
                    "#### Regras de Negócio:\n" +
                    "- **Bloqueio de Configuração (`configure`):** É terminantemente proibido que clientes móveis/front-end criem, alterem ou deletem estruturas (filas/exchanges) no servidor. Qualquer tentativa gera `deny`.\n" +
                    "- **Acesso a Tópicos (`read` / `write`):** Operações de leitura (consumir) e escrita (publicar) só são estritamente autorizadas se o tipo do recurso físico em questão for do tipo **`topic`**.",
            tags = {"RabbitMQ Internal Auth"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "O RabbitMQ interpretará o corpo textual da resposta para decidir o acesso:\n" +
                            "- `allow`: A operação de leitura/escrita é em um recurso do tipo 'topic'. Acesso permitido.\n" +
                            "- `deny`: Tentativa de ação do tipo 'configure' ou operação em recursos que não são do tipo 'topic'. Acesso recusado.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "allow")))
    })
    @PostMapping(value = "/resource", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateResource(
            @Parameter(description = "ID do usuário (UUID) executando a ação")
            @RequestParam("user") String username,
            @Parameter(description = "Virtual Host onde o recurso está localizado", example = "/")
            @RequestParam("vhost") String vhost,
            @Parameter(description = "O tipo do recurso do RabbitMQ (ex: 'topic', 'queue')", example = "topic")
            @RequestParam("resource") String resource,
            @Parameter(description = "O nome específico da Exchange ou Fila alvo", example = "amq.topic")
            @RequestParam("name") String name,
            @Parameter(description = "O tipo de operação sendo realizada ('configure', 'write', 'read')", example = "write")
            @RequestParam("permission") String permission) {
        boolean verifyResourcePermissions = rabbitMQAuthService.authenticateResource(username, vhost, resource, name, permission);

        return verifyResourcePermissions ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

    @Operation(
            summary = "[RabbitMQ] Segurança Cirúrgica de Canais (Topic)",
            description = "### ATENÇÃO: Rota utilizada exclusivamente pelo servidor RabbitMQ ###\n\n" +
                    "Este endpoint **não deve ser chamado diretamente pelo cliente (front-end)**. Ele é invocado internamente pelo broker RabbitMQ para validar se o usuário logado possui direito de publicar ou assinar um tópico específico, baseado na estrutura da **Routing Key**.\n\n" +
                    "#### Como a Segurança Viva Funciona:\n" +
                    "O backend intercepta a Chave de Roteamento (ex: `v1.gps.cidadeId.viagemId`), extrai o ID da viagem (último segmento) e aplica as seguintes travas:\n" +
                    "- **Publicação (`publish`):** Geralmente realizada pelo app do Motorista. O sistema valida se o ID do usuário conectado é de fato o **motorista escalado** para aquela viagem específica.\n" +
                    "- **Inscrição (`subscribe`):** Geralmente realizada pelo app do Estudante. O sistema valida se o ID do usuário conectado é de um **estudante vinculado** a essa viagem.\n\n" +
                    "#### Nota para o Front-end (Formato da Chave):\n" +
                    "Qualquer divergência na montagem da chave ou tentativa de se inscrever/publicar em viagens de terceiros resultará em bloqueio imediato (`deny`) pelo Broker.",
            tags = {"RabbitMQ Internal Auth"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "O RabbitMQ interpretará o corpo textual da resposta para decidir o acesso:\n" +
                            "- `allow`: O usuário possui o vínculo correto com a viagem informada na chave.\n" +
                            "- `deny`: Usuário não vinculado à viagem, operação inválida ou falha na estrutura da chave.",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "allow")))
    })
    @PostMapping(value = "/topic", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authenticateTopic(
            @Parameter(description = "ID do usuário autenticado (UUID) que está interagindo com o tópico")
            @RequestParam("authenticatedUserId") String usernameId,
            @Parameter(description = "A chave de roteamento do canal de tempo real (ex: v1.gps.cityId.travelId)", example = "v1.gps.789012-xyz.123e4567-e89b-12d3-a456-426614174000")
            @RequestParam("routing_key") String routingKey,
            @Parameter(description = "O tipo de operação no canal ('publish' para enviar ou 'subscribe' para escutar)", example = "subscribe")
            @RequestParam("permission") String permission) {
        boolean isTopicAuth = rabbitMQAuthService.authenticateTopic(usernameId, routingKey, permission);

        return isTopicAuth ? ResponseEntity.ok("allow") : ResponseEntity.ok("deny");
    }

}
