package com.travel_system.backend_app.service;

import com.travel_system.backend_app.interfaces.PrivateStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class DocumentPersistenceService {
    private final Logger log = LoggerFactory.getLogger(DocumentPersistenceService.class);

    private final PrivateStorageService privateStorageService;
    private final TransactionTemplate transactionTemplate;

    public DocumentPersistenceService(PrivateStorageService privateStorageService, TransactionTemplate transactionTemplate) {
        this.privateStorageService = privateStorageService;
        this.transactionTemplate = transactionTemplate;
    }

    /*
     * rotina genérica de gravação de arquivos privados
     * - envia o arquivo ao storage e executa a persistência dentro de uma transação
     * - o que persistir fica a cargo de quem chama (callback)
     * - se a transação falhar, o arquivo enviado é removido (compensação) e a exception é relançada
     * */
    public <T> T storeAndPersist(byte[] content, String storageKey, String contentType, Supplier<T> persistence) {
        privateStorageService.upload(content, storageKey, contentType);

        try {
            return transactionTemplate.execute(status -> persistence.get());
        } catch (RuntimeException e) {
            removeUploadedFile(storageKey);
            throw e;
        }
    }

    // compensação: remove o arquivo do storage quando a gravação no banco falha
    private void removeUploadedFile(String storageKey) {
        try {
            privateStorageService.delete(storageKey);
        } catch (RuntimeException e) {
            // não mascara o erro original; o arquivo órfão fica registrado para limpeza
            log.error("[upload] Falha ao remover arquivo órfão do storage. Key: {}", storageKey, e);
        }
    }

}
