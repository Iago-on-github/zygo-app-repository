package com.travel_system.backend_app.model.enums;

public enum DocumentType {
    MEDICAL_REPORT(DocumentCategory.HEALTH),
    EMERGENCY_ACTION_PLAN(DocumentCategory.HEALTH),
    PRESCRIPTION(DocumentCategory.HEALTH),
    OTHER(DocumentCategory.HEALTH),

    // matrícula de transporte (apagados após a revisão)
    IDENTITY_DOCUMENT(DocumentCategory.ENROLLMENT),
    INSTITUTION_ENROLLMENT_PROOF(DocumentCategory.ENROLLMENT);

    private final DocumentCategory category;

    DocumentType(DocumentCategory category) {
        this.category = category;
    }

    public DocumentCategory getCategory() {
        return category;
    }
}

/*
* GUIDE:
* - MEDICAL_REPORT: laudo ou relatório médico que comprova o diagnóstico (defic. física, epilepsia, TEA, etc)
* - EMERGENCY_ACTION_PLAN: plano de ação para emergências em casos de reações graves como: asma, alergia grave, epilepsia - como agir nessa situação
* - PRESCRIPTION: receita médica para medicações contínuas ou em emergências que o aluno carrega
* - OTHER: outros tipos de casos nos quais não se encaixam nas categorias atuais de saude
*
* - INSTITUTION_ENROLLMENT_PROOF: matrícula no ônibus
*
* */

