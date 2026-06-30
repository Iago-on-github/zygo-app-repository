package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/v1/current")
public class CurrentUserController {
    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Operation(
            summary = "Update de foto do perfil",
            description = "Atualiza a foto de perfil do usuário atualmente logado",
            tags = {"CurrentUsers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Foto de perfil atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Invalid Request. Possíveis causas:\n" +
                    "- **Null:** Arquivo não informado ou vazio.\n" +
                    "- **Parâmetros inválidos:** Parâmetros necessários nulos ou inválidos.;\n"),
            @ApiResponse(responseCode = "401", description = "Not Authenticated. Token JWT ausente, expirado ou inválido."),
            @ApiResponse(responseCode = "404", description = "Resource Not Found. Possíveis causas:\n" +
                    "- **Entidade não encontrada:** Entidade não encontrada na base de dados;\n"),
            @ApiResponse(responseCode = "415", description = "Invalid Type: O arquivo enviado não é uma imagem válida ou utiliza um formato não suportado."),
            @ApiResponse(responseCode = "503", description = "Service Unavailable: Falha ao enviar a imagem para o serviço de armazenamento.")
    })
    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProfilePicture(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException {
        String email = auth.getName();

        currentUserService.userProfilePictureUpdate(email, file);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remover foto de perfil",
            description = "Remove a foto de perfil do usuário logado",
            tags = {"CurrentUsers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Foto de perfil removida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Invalid Request. Possíveis causas:\n" +
                    "- **Parâmetros inválidos:** Parâmetros necessários nulos ou inválidos."),
            @ApiResponse(responseCode = "401", description = "Not Authenticated. Token JWT ausente, expirado ou inválido."),
            @ApiResponse(responseCode = "404", description = "Not Found: Usuário não possui foto de perfil."),
            @ApiResponse(responseCode = "503", description = "Service Unavailable: Falha ao enviar a imagem para o serviço de armazenamento.")
    })
    @PutMapping(value = "/delete")
    public ResponseEntity<Void> deleteProfilePicture(Authentication auth) {
        String email = auth.getName();

        currentUserService.userProfilePictureDelete(email);
        return ResponseEntity.noContent().build();
    }
}
