package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.DocumentCategory;
import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.repository.RegistrationDocumentAuditRepository;
import com.travel_system.backend_app.repository.RegistrationRequestRepository;
import com.travel_system.backend_app.repository.TransportRegistrationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RegistrationDataCleanupService {

    private final RegistrationDocumentAuditRepository registrationDocumentAuditRepository;
    private final DocumentRepository documentRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final TransportRegistrationRepository transportRegistrationRepository;

    private final ApplicationEventPublisher eventPublisher;

    public RegistrationDataCleanupService(RegistrationDocumentAuditRepository registrationDocumentAuditRepository, DocumentRepository documentRepository, RegistrationRequestRepository registrationRequestRepository, TransportRegistrationRepository transportRegistrationRepository, ApplicationEventPublisher eventPublisher) {
        this.registrationDocumentAuditRepository = registrationDocumentAuditRepository;
        this.documentRepository = documentRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.eventPublisher = eventPublisher;
    }

    /*
    * apaga todos os dados de matrícula de um motorista ou aluno quando ele sai do customer
    *
    * deleta em ordem por conta das fks
    *
    * */

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllByOwner(OwnerBuilt ownerBuilt) {
        List<Document> documents = documentRepository.findAllByCategoryAndOwnerId(ownerBuilt.id(), DocumentCategory.ENROLLMENT);
        List<RegistrationRequest> requests = registrationRequestRepository.findAllByOwnerId(ownerBuilt.id());
        List<TransportRegistration> registrations = transportRegistrationRepository.findAllByOwnerId(ownerBuilt.id());

        Set<String> keys = new HashSet<>();

        List<UUID> requestIds = new ArrayList<>();

        for (Document doc : documents) {
            keys.add(doc.getStorageKey());
        }

        for (RegistrationRequest request : requests) {
            if (request.getPendingPhotoStorageKey() != null) {
                keys.add(request.getPendingPhotoStorageKey());
            }

            requestIds.add(request.getId());
        }

        for (TransportRegistration registration : registrations) {
            if (registration.getPhotoStorageKey() != null) {
                keys.add(registration.getPhotoStorageKey());
            }
        }

        if (!requestIds.isEmpty()) {
            // apaga as audit que apotam para as solicitações realizadas pelo usuário
            registrationDocumentAuditRepository.deleteAllByRegistrationRequestId(requestIds);
        }

        // apaga os documentos (apenas enrollment aqui) das solicitações do usuário
        documentRepository.deleteAll(documents);

        // apaga todas as solicitações
        registrationRequestRepository.deleteAll(requests);

        // apaga todos os registros principais
        transportRegistrationRepository.deleteAll(registrations);

        if (!keys.isEmpty()) {
            // evento para as keys dos docs
            eventPublisher.publishEvent(new DocumentFilesDeletionRequestedEvent(List.copyOf(keys)));
        }
    }

}
