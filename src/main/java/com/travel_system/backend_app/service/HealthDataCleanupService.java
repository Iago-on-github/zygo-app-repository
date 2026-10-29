package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.DocumentCategory;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.HealthConsentRepository;
import com.travel_system.backend_app.repository.HealthDeclarationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/*
* usado para deletar todos os dados de saúde de um user ao sair do Customer
* */

@Component
public class HealthDataCleanupService {

    private final DocumentRepository documentRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;
    private final HealthConsentRepository healthConsentRepository;

    private final ApplicationEventPublisher eventPublisher;

    public HealthDataCleanupService(DocumentRepository documentRepository, HealthDeclarationRepository healthDeclarationRepository, HealthConsentRepository healthConsentRepository, ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.healthConsentRepository = healthConsentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void deleteAllByOwner(OwnerBuilt owner) {
        List<Document> allOwnerDocs = documentRepository.findAllByCategoryAndOwnerId(owner.id(), DocumentCategory.HEALTH);

        List<String> storageKeys = new ArrayList<>();
        for (Document doc : allOwnerDocs) {
            storageKeys.add(doc.getStorageKey());
        }

        // apaga os docs de todas as categorias
        documentRepository.deleteAll(allOwnerDocs);

        // força sicronização
        documentRepository.flush();

        /*
        * apaga as declarações + as condições que saem pelo ophanRemoval
        * */
        healthDeclarationRepository.findByOwnerId(owner.id()).ifPresent(healthDeclarationRepository::delete);

        /*
        * apaga os consentmentos em geral, até os revogados
        * */
        healthConsentRepository.deleteAll(healthConsentRepository.findAllByOwnerId(owner.id()));

        // chama evento de deleção dos docs
        if (!storageKeys.isEmpty()) {
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(storageKeys));
        }

    }
}
