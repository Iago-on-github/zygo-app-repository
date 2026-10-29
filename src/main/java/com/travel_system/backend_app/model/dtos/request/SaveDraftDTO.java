package com.travel_system.backend_app.model.dtos.request;
/*
* dto que serve para salvar os dados no campo de formulário para que o user nao precise preencher tudo dnv ao fechar o app
* */

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SaveDraftDTO(
    @Size(max = 20)
    String identityNumber,
    @Size(max = 20)
    String identityIssuer,
    @PastOrPresent
    @JsonFormat(pattern = "dd/MM/yyyy")
    LocalDate identityIssuedAt,
    @Size(max = 60)
    String motherName,
    @Size(max = 60)
    String fatherName
) {
}
