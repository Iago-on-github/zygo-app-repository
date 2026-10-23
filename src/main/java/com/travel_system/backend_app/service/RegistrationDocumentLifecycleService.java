package com.travel_system.backend_app.service;

/*
* responsável por gerenciar a visualização e o tempo de vida dos documentos sensíveis de registro no sistema
* os documentos são requisitados mas, após serem processados (manualmente) devem ser completamente apagados do sistema
* */

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.RegistrationDocumentAudit;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.model.enums.RegistrationReviewOutcome;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.RegistrationDocumentAuditRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * ciclo de vida dos documentos de matrícula para a categoria ENROLLMENT
 * - os documentos existem apenas enquanto a solicitação está aberta (DRAFT / UNDER_REVIEW)
 * - revisão concluída (aprovação ou rejeição) grava a auditoria e apaga os documentos
 * - cancelamento (usuário ou limpeza de rascunhos) apaga os documentos sem auditoria
 * - reenvio do mesmo tipo na mesma solicitação o anterior é apagado na hora (não há motivo para guardá-lo)
 * - os arquivos são removidos do storage somente após o commit (listener AFTER_COMMIT)
 * */

@Component
public class RegistrationDocumentLifecycleService {

    private static final Set<DocumentStatus> ACTIVE_STATUSES = EnumSet.of(DocumentStatus.PENDING_REVIEW, DocumentStatus.APPROVED);

    private final DocumentRepository documentRepository;
    private final RegistrationDocumentAuditRepository registrationDocumentAuditRepository;

    private final ApplicationEventPublisher eventPublisher;

    public RegistrationDocumentLifecycleService(DocumentRepository documentRepository, RegistrationDocumentAuditRepository registrationDocumentAuditRepository, ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.registrationDocumentAuditRepository = registrationDocumentAuditRepository;
        this.eventPublisher = eventPublisher;
    }

    /*
     * chamado na aprovação e na rejeição da solicitação e
     * - grava um registro de auditoria por documento (sem nenhum dado do conteúdo)
     * - apaga os documentos (linhas + arquivos)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeReviewedDocuments(RegistrationRequest request, UUID reviewerAccountId, RegistrationReviewOutcome outcome) {
        List<Document> documents = documentRepository.findAllByRegistrationRequestId(request.getId());

        if (documents.isEmpty()) {
            return;
        }

        Instant reviewedAt = Instant.now();

        // auditoria
        List<RegistrationDocumentAudit> audits = documents.stream().map(document -> toAudit(document, reviewerAccountId, outcome, reviewedAt))
                .toList();

        registrationDocumentAuditRepository.saveAll(audits);

        deleteDocuments(documents);
    }

    /*
     * chamado no cancelamento da solicitação (pelo usuário ou pelo job de rascunhos abandonados)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardRequestDocuments(UUID registrationRequestId) {
        deleteDocuments(documentRepository.findAllByRegistrationRequestId(registrationRequestId));
    }

    /*
     * chamado no upload, antes de inserir o novo documento
     * - apaga o documento ativo do mesmo tipo na mesma solicitação
     * - o flush garante que a exclusão chegue ao banco antes do insert (índice único parcial)
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardPreviousOfType(UUID registrationRequestId, DocumentType documentType) {
        documentRepository.findActiveByRegistrationRequestIdAndType(registrationRequestId, documentType, ACTIVE_STATUSES)
                .ifPresent(previous -> deleteDocuments(List.of(previous)));
    }

    // apaga as linhas agora e os arquivos após o commit
    private void deleteDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }

        List<String> storageKeys = documents.stream().map(Document::getStorageKey).toList();

        documentRepository.deleteAll(documents);
        documentRepository.flush();

        eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(storageKeys));
    }

    // registro mínimo: tipo, hash, quem enviou, quem conferiu e quando
    private RegistrationDocumentAudit toAudit(Document document, UUID reviewerAccountId, RegistrationReviewOutcome outcome, Instant reviewedAt) {
        RegistrationDocumentAudit audit = new RegistrationDocumentAudit();

        audit.assignCustomer(document.getCustomerId());
        audit.setRegistrationRequestId(document.getRegistrationRequestId());
        audit.setDocumentType(document.getDocumentType());
        audit.setSha256(document.getSha256());
        audit.setUploadedBy(document.getUploadedBy());
        audit.setUploadedAt(document.getCreatedAt());
        audit.setReviewedBy(reviewerAccountId);
        audit.setReviewedAt(reviewedAt);
        audit.setOutcome(outcome);

        return audit;
    }
}
