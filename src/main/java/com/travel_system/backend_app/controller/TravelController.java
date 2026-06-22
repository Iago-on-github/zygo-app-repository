package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.service.TravelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/travel")
public class TravelController {

    private final TravelService travelService;

    public TravelController(TravelService travelService) {
        this.travelService = travelService;
    }

    @Operation(
            summary = "Realiza a ação de criar nova viagem.",
            description = "Registra uma nova viagem no sistema para o motorista informado, consumindo a API do Mapbox para gerar o preview estático de distância, tempo estimado e cidade de destino.\n" +
                    "### Regras de Negócio:\n" +
                    "- **Validação de Estado:** O motorista deve estar ativamente cadastrado no sistema (`ACTIVE`).\n" +
                    "- **Trava de Simultaneidade:** O motorista **não pode** possuir nenhuma outra viagem em andamento ou agendada (status `PENDING` ou `TRAVELLING`). Caso possua, a criação será bloqueada.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Viagem criada com sucesso.",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TravelResponseDTO.class)),
                headers = @Header(name = "Location", description = "URI da viagem recém criada (ex.: /v1/travel/{id}",
                        schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida. Possíveis causas:\n" +
                            "- O corpo do JSON está malformado ou violou as regras de validação;\n" +
                            "- **Motorista Inativo:** O motorista informado está com status `INACTIVE` no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_DRIVER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Motorista não encontrado no banco de dados através do ID fornecido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "Motorista já possui outra viagem em andamento no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/create")
    public ResponseEntity<TravelResponseDTO> createTravel(@RequestBody TravelRequestDTO travelRequestDTO, UriComponentsBuilder componentsBuilder) {
        TravelResponseDTO responseDTO = travelService.createTravel(travelRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(responseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(responseDTO);
    }

    @Operation(
            summary = "Realiza a ação de iniciar a viagem",
            description = "Faz a transição de estado da viagem para execução real, calculando a geometria oficial utilizada para o trajeto (polyline) que o mapa irá usar. \n " +
                    "### Regras de Negócio: \n" +
                    "- **Estado da viagem**: Validando a existência da viagem, é verificado o seu STATUS: se for `PENDING` o processamento continua. Em casos de viagens `TRAVELLING` ou `FINISH` o backend lança exception. \n" +
                    "- **Integração externa**: Chama o mapbox para recalcular a rota exata. Caso a API externa falhe ou retorne dados insuficientes/incompletos, o sistema não inicializa a viagem lançando exception. \n" +
                    "- **Uso de cache**: o backend salva o ID da viagem atualmente ativa no redis para iniciar métricas de monitoramento e  prepara o cluster para receber os pings contínuos de GPS.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Viagem iniciada com sucesso. Sem body retornado.",
                content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_DRIVER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Viagem não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "A viagem já está em andamento (`TRAVELLING`) ou finalizada (`FINISH`) no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "MapboxAPI retornou dados insuficientes, inválidos ou falhou na requisição.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{travelId}/start")
    public ResponseEntity<Void> startTravel(@PathVariable UUID travelId) {
        travelService.startTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Realiza a ação de encerrar a viagem",
            description = "Faz a transição de estado da viagem para `FINISH`, desvinculando todos os estudantes e persistindo toda a telemetria em um relatório final. \n" +
                    "### Regras de Negócio: \n" +
                    "- **Estado da viagem**: Validando a existência da viagem, é verificado o seu STATUS: se for `TRAVELLING` o processamento continua. Em casos de viagens `PENDING` ou `FINISH` o backend lança exception. \n" +
                    "- **Desembarque**: Atua sob o Set de estudantes embarcados na viagem e, para cada um, realiza a ação de desvinculo, registrando as métricas de desembarque. \n" +
                    "- **Consolidação de Trajeto (Polyline):** Compila o histórico de coordenadas coletadas pelo GPS, converte os pontos geográficos e gera uma String de Polyline codificada para o relatório final.\n" +
                    "- **Cálculo de Telemetria e Métricas:** Resgata do Redis a distância real acumulada da viagem, calcula a duração exata, a quantidade total de passageiros e o percentual de eficiência de lotação, persistindo tudo na tabela de relatórios.\n" +
                    "- **Limpeza de Cache e Histórico:** Remove os registros temporários de localização do banco relacional e limpa o cache de tracking do Redis para otimização de memória.\n" +
                    "> ### IMPORTANTE:\n" +
                    "> Com o retorno de sucesso (204) deste endpoint, o aplicativo do celular do motorista **deve interromper imediatamente** o envio de pings de GPS para o servidor.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Viagem finalizada com sucesso. Nenhum body retornado.",
                content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_DRIVER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Viagem não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "A viagem não está registrada como em andamento (`TRAVELLING)`.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{travelId}/end")
    public ResponseEntity<Void> endTravel(@PathVariable UUID travelId) {
        travelService.endTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Realiza a ação de vincular o estudante",
            description = "Realiza a ação de vincular o estudante na viagem em tempo real. \n" +
                    "### Regras de Negócio: \n" +
                    "- **Estudante**: Utiliza o Email do estudante autenticado extraído do próprio contexto de Autenticação. \n" +
                    "- **Estado da viagem**: Validando a existência da viagem, é verificado o seu STATUS: se for `TRAVELLING` o processamento continua. Em casos de viagens `PENDING` ou `FINISH` o backend lança exception. \n" +
                    "- **Duplicidade**: Backend realiza uma validação de segurança para verificar se o estudante já não está vinculado a essa viagem. \n" +
                    "- **Vínculo**: Cria entidade StudentTravel, definindo o status de embarque como ativo e registrando a hora exata da entrada.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Estudante vinculado com sucesso. Nenhum body retornado.",
                content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_USER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404",
                    description = "Recurso não encontrado. Possíveis causas:\n" +
                            "- **Viagem não encontrada**: A viagem informada na URL não existe no sistema;\n" +
                            "- **Estudante não localizado**: O estudante dono do token JWT não foi localizado no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409",
                    description = "Conflito na viagem. Possíveis causas: \n" +
                            "- **Estado da viagem**: A viagem não está registrada como em andamento (`TRAVELLING)`. \n" +
                            "- **Duplicidade de vínculo**: Estudante já está conectado a esta viagem.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{travelId}/join")
    public ResponseEntity<Void> joinTravel(@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.joinTravel(travelId, studentEmail, StudentTravelStatus.ACTIVE);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Realiza a ação de desvincular o estudante",
            description = "Realiza o desembarque voluntário e/ou conclusão de percurso de um estudante, desvinculando-o do rastreamento. \n" +
                    "### Regras de Negócio: \n" +
                    "- **Estado da viagem**: Validando a existência da viagem, é verificado o seu STATUS: se for `TRAVELLING` o processamento continua. Em casos de viagens `PENDING` ou `FINISH` o backend lança exception. \n" +
                    "- **Validação de vínculo**: O sistema realiza validações para comprovar se o estudante está ativo na viagem. Se houver tentativa de de \"leave\" em uma viagem onde ele não está inserindo o backend lança exception. \n" +
                    "- **Registro de saída**: Altera o embarque para false, registra o momento exato do desembarque e assina o motivo da saída no banco de dados, definido de forma padrão pelo controller como `LEFT`. \n" +
                    "### Importante: \n" +
                    "O sistema possui um algoritmo de auto-desvinculo de estudantes, quando um estudante é desvinculado pelo 'leaveTravel' ele deve respeitar a decisão padrão do Controller para setar o Status como `LEFT`.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "desvinculo do estudante realizado com sucesso. Sem body retornado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_USER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado. Possíveis causas: \n" +
                    "- **Estudante não localizado**: O estudante dono do token JWT não foi localizado no banco de dados. \n" +
                    "- **Estudante desvinculado**: O estudante não está atualmente ativo na viagem (isEmbark = false). \n" +
                    "- **Vínculo não encontrado**: Nenhum vínculo entre o estudante e a viagem foi encontrado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "A viagem não está registrada como em andamento (`TRAVELLING)`.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{travelId}/leave")
    public ResponseEntity<Void> leaveTravel (@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.leaveTravel(travelId, studentEmail, StudentTravelStatus.LEFT);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Obter dados de preview e previsão da viagem",
            description = "Retorna os dados, de forma resumida, de distância, tempo e destino da viagem. Será usado para apresentar um breve preview da viagem. \n" +
                    "### Regras de Negócio: \n" +
                    "- **Busca pela viagem**: Realiza uma busca pela viagem no banco, se não existir lança exception. \n" +
                    "### Cálculo dinâmico: O sistema calcula o dado de 'arrivalTime' de duas formas: \n" +
                    "1. **Viagem criada**: O sistema utiliza da métrica de 'createdAt' da viagem que acabou de ser `criada` para fazer a exibição. \n" +
                    "2. **Viagem iniciada**: O sistema utiliza da métrica de 'startHourTravel' da viagem que acabou de ser `inicializada` para fazer a exibição. \n" +
                    " ### Por que essa diferenciação? \n" +
                    "As viagens serão criadas antes do trajeto em si começar. Geralmente, os motoristas chegam de 5-10m antes de do horário de saída para aguardar os estudantes.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "ados de preview obtidos com sucesso. Retorna a estrutura com o cálculo do horário estimado de chegada.",
                    content = @Content(schema = @Schema(implementation = TravelPreviewDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(mediaType = "application/json", schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Viagem não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
    })
    @GetMapping("/{travelId}/preview")
    public ResponseEntity<TravelPreviewDTO> getTravelPreview(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelService.getTravelPreview(travelId));
    }
}
