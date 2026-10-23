package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.FileTooLargeException;
import com.travel_system.backend_app.exceptions.ImageProcessingException;
import com.travel_system.backend_app.model.enums.ImageUploadPolicy;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
public class ImageProcessingService {
    private final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);

    private final Tika tika = new Tika();

    private final AntivirusScanner antivirusScanner;

    // formatos aceitos no envio
    private static final Set<String> ACCEPTED_INPUT_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    // limite de pixels aceitos para evitar pngs abusivos
    private static final int PX_GLOBAL_LIMIT = 8000;

    // qualidade final (limiar entre 0.0 e 1.0)
    private static final float JPEG_QUALITY = 0.85f;

    private static final long MAX_PROFILE_UPLOAD_SIZE_BYTES = 150 * 1024;
    private static final long MAX_ENROLLMENT_UPLOAD_SIZE_BYTES = 4 * 150 * 1024;

    public ImageProcessingService(AntivirusScanner antivirusScanner) {
        this.antivirusScanner = antivirusScanner;
    }


    /*
     * converte a imagem recebida para JPEG, aplicando a política do tipo de imagem
     * - valida tamanho, tipo real (tika) e antivírus
     * - valida as dimensões antes de decodificar
     * - redimensiona para o maior lado da política (mantendo a proporção)
     * - recodifica em JPEG: transparência vira fundo branco e os metadados (EXIF) são descartados
     * */
    public byte[] convertImageToJPEG(MultipartFile file, ImageUploadPolicy policy) {
        byte[] bytes = process(file, policy);

        validateDimensions(bytes);

        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new ImageProcessingException("Não foi possível ler a imagem enviada", e);
        }

        if (originalImage == null) {
            throw new UnsupportedMediaTypeException("[convertImageToJPEG] originalImage null ou inválida");
        }

        BufferedImage resizedImage = resizeToRgb(originalImage, policy.getMaxSidePx());

        return writeJpeg(resizedImage);
    }

    /*
     * validações do arquivo recebido
     * - devolve os bytes lidos para que o restante do fluxo não leia o arquivo novamente
     * */
    private byte[] process(MultipartFile file, ImageUploadPolicy policy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (file.getSize() > policy.getMaxUploadBytes()) {
            throw new FileTooLargeException("O arquivo é muito grande. Uploads aceitos são de até " + (policy.getMaxUploadBytes() / 1024) + " KB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageProcessingException("Não foi possível ler o arquivo enviado", e);
        }

        // detecta o tipo real do conteúdo com o tika através dos magic bytes
        String detect = tika.detect(bytes);

        if (!ACCEPTED_INPUT_CONTENT_TYPES.contains(detect)) {
            log.warn("[process] tipo de arquivo não permitido. Detect: {} ", detect);
            throw new UnsupportedMediaTypeException("Formato não suportado. Envie um tipo válido: " + ACCEPTED_INPUT_CONTENT_TYPES);
        }

        // verifica no antivirus
        antivirusScanner.scan(bytes);

        return bytes;
    }

    /*
     * valida se uma imagem pequena não foi enviada com excesso de pixels
     * - lê apenas o cabeçalho (largura/altura), sem decodificar a imagem inteira
     * */
    private void validateDimensions(byte[] bytes) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInputStream == null) {
                throw new ImageProcessingException("Não foi possível ler a imagem enviada");
            }

            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
            if (!imageReaders.hasNext()) {
                throw new UnsupportedMediaTypeException("Formato de imagem não suportado");
            }

            ImageReader reader = imageReaders.next();
            try {
                reader.setInput(imageInputStream);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());

                if (width > PX_GLOBAL_LIMIT || height > PX_GLOBAL_LIMIT) {
                    throw new ImageProcessingException("A imagem contém mais pixels do que o permitido pelo sistema");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ImageProcessingException("Ocorreu um erro durante o processamento da imagem", e);
        }
    }

    /*
     * redimensiona mantendo a proporção (nunca amplia) e converte para RGB com fundo branco
     * */
    private BufferedImage resizeToRgb(BufferedImage originalImage, int maxSidePx) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // fator de escala: 1.0 quando a imagem já cabe no limite
        double scale = Math.min(1.0, (double) maxSidePx / Math.max(width, height));

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage rgbImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics2D = rgbImage.createGraphics();
        try {
            // interpolação de qualidade para a redução não ficar serrilhada
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, Color.WHITE, null);
        } finally {
            graphics2D.dispose();
        }

        return rgbImage;
    }

    /*
     * grava em JPEG com qualidade explícita
     * */
    private byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();

        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new ImageProcessingException("Não foi possível gerar a imagem final", e);
        } finally {
            writer.dispose();
        }

        return outputStream.toByteArray();
    }

}
