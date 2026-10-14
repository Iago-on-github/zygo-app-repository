package com.travel_system.backend_app.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.travel_system.backend_app.exceptions.DocumentProcessingException;
import com.travel_system.backend_app.exceptions.FileTooLargeException;
import com.travel_system.backend_app.model.dtos.security.ProcessedDocument;
import okio.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class DocumentProcessingService {
    private final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    // formato gravado no storage via conversão sempre gera JPEG
    private static final String PDF = "application/pdf";
    private static final long MAX_FILE_SIZE  = 10 * 1024 * 1024;

    private final Tika tika = new Tika();

    private final AntivirusScanner antivirusScanner;

    public DocumentProcessingService(AntivirusScanner antivirusScanner) {
        this.antivirusScanner = antivirusScanner;
    }

    /*
     *  responsável por validar e preparar o documento para o storage:
     * - valida tamanho e tipo real (pelo conteúdo, nunca pelo Content-Type ou extensão)
     * - aceita somente PDF, mantido como foi enviado
     * - verifica no antivírus antes de seguir para o storage
     * - tudo em memória: nenhum arquivo temporário é gravado em disco
     * */
    public ProcessedDocument process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileTooLargeException("O arquivo é muito grande. Uploads aceitos são de até 10MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new DocumentProcessingException("Não foi possível ler o arquivo enviado", e);
        }

        // detecta o tipo real pelo conteúdo com o Tika (magic bytes)
        String detect = tika.detect(bytes);

        if (!PDF.equals(detect)) {
            log.warn("[process] Tipo de arquivo não permitido. Detectado: {}", detect);
            throw new UnsupportedMediaTypeException("Formato não suportado. Envie um arquivo PDF");
        }

        // verifica no antivírus
        antivirusScanner.scan(bytes);

        return new ProcessedDocument(bytes, PDF, bytes.length, sha256(bytes));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {

            // SHA-256 é obrigatório em toda JVM
            throw new IllegalStateException(e);
        }
    }

}
