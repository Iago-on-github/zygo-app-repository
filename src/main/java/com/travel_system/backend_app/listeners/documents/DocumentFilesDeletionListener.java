package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.DocumentFilesDeletionRequestedEvent;
import com.travel_system.backend_app.service.PendingDocsExclusionsService;
import com.travel_system.backend_app.service.S3PrivateStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentFilesDeletionListener {
    private final Logger log = LoggerFactory.getLogger(DocumentFilesDeletionListener.class);

    private final PendingDocsExclusionsService pendingDocsExclusionsRepository;
    private final S3PrivateStorageService privateStorageService;

    public DocumentFilesDeletionListener(PendingDocsExclusionsService pendingDocsExclusionsRepository, S3PrivateStorageService privateStorageService) {
        this.pendingDocsExclusionsRepository = pendingDocsExclusionsRepository;
        this.privateStorageService = privateStorageService;
    }

    /*
    * apaga os arquivos do Storage, caso falhe registra nova key para uma nova tentativa
    * que é processada por um job rodando de hora em hora
    * */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteFilesFromStorage(DocumentFilesDeletionRequestedEvent event) {
        for (String key : event.storageKeys()) {
            // tenta remover
            try {
                privateStorageService.delete(key);
            } catch (Exception e) {
                // caso falhe, registra falha e job diário roda para tentar deletar
                log.warn("[deleteFilesFromStorage] erro ao deletar arquivo do storage");

                pendingDocsExclusionsRepository.register(key, e);
            }
        }
    }

}
