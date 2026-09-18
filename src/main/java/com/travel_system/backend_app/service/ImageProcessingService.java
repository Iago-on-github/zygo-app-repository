package com.travel_system.backend_app.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageProcessingService {

    public byte[] convertImageToJPEG(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("[convertImageToJPEG] file não pode estar null ou inválido");
        }

        if (!isImage(file)) throw new UnsupportedMediaTypeException("[convertImageToJPEG] não é uma imagem");

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(inputStream);

            if (originalImage == null) throw new UnsupportedMediaTypeException("[convertImageToJPEG] originalImage null ou inválida");

            BufferedImage rgbImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D graphics2D = rgbImage.createGraphics();
            graphics2D.drawImage(originalImage, 0, 0, Color.white, null);
            graphics2D.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpeg", outputStream);

            return outputStream.toByteArray();
        }
    }

    // verifica se é imagem a partir do tipo "MIME" retornado pelo content-type
    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();

        return contentType != null && (
                        contentType.equals("image/jpeg") ||
                        contentType.equals("image/png") ||
                        contentType.equals("image/webp")
        );
    }
}
