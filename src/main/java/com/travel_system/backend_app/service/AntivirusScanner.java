package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.ClamAvProperties;
import com.travel_system.backend_app.exceptions.AntivirusUnavailableException;
import com.travel_system.backend_app.exceptions.InfectedFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AntivirusScanner {
    private final Logger log = LoggerFactory.getLogger(AntivirusScanner.class);

    private final ClamavClient clamavClient;
    private final ClamAvProperties clamAvProperties;
    private final ThreadPoolTaskExecutor executor;

    public AntivirusScanner(ClamavClient clamavClient, ClamAvProperties clamAvProperties, @Qualifier("antivirusExecutor") ThreadPoolTaskExecutor executor) {
        this.clamavClient = clamavClient;
        this.clamAvProperties = clamAvProperties;
        this.executor = executor;
    }

    /*
     *  responsável por verificar o arquivo no ClamAV, steps:
     * - só a resposta OK libera o arquivo
     * - vírus encontrado: lança InfectedFileException (422)
     * - qualquer falha (conexão, timeout, fila cheia, resposta inesperada): lança AntivirusUnavailableException (503)
     * */
    public void scan(byte[] contentBytes) {
        if (contentBytes == null || contentBytes.length == 0) {
            throw new IllegalArgumentException("Conteúdo do arquivo não pode ser vazio para o scan");
        }

        // submete o scan ao executor dedicado (a biblioteca não expõe timeout próprio)
        Future<ScanResult> future;
        try {
            future = executor.submit(() ->
                    clamavClient.scan(new ByteArrayInputStream(contentBytes)));
        } catch (TaskRejectedException e) {
            log.error("[antivirus] Fila de scan cheia, upload recusado");
            throw new AntivirusUnavailableException("Fila de scan cheia", e);
        }

        // aguarda o resultado respeitando o timeout configurado
        ScanResult result;
        try {
            result = future.get(clamAvProperties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("[antivirus] Timeout de {} ao aguardar o scan", clamAvProperties.getTimeout());

            throw new AntivirusUnavailableException("Timeout no scan", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);

            throw new AntivirusUnavailableException("Scan interrompido", e);
        } catch (ExecutionException e) {
            // conexão recusada, ClamavException, erro de protocolo etc.
            log.error("[antivirus] Falha ao executar o scan", e.getCause());
            throw new AntivirusUnavailableException("Falha no scan", e.getCause());
        }

        // somente OK libera o arquivo
        if (result instanceof ScanResult.OK) {
            return;
        }

        if (result instanceof ScanResult.VirusFound virusFound) {
            // a assinatura vai apenas para o log, nunca para a resposta ao cliente
            log.warn("[antivirus] Arquivo infectado detectado. Assinaturas: {}", virusFound.getFoundViruses().values());
            throw new InfectedFileException("O arquivo enviado não pôde ser aceito");
        }

        // qualquer outro resultado é tratado como falha (falha fechada)
        log.error("[antivirus] Resultado inesperado do scan: {}", result);
        throw new AntivirusUnavailableException("Resultado inesperado do scan");
    }
}

/*
 * resonsável pela detecção de virus em arquivos enviados pelos users
 *
 * manutenção:
 * - a lib é escrita 100% em kotlin, então se a ide reclamar de algo sempre verifique os imports e se os nomes estã corretos
 * */
