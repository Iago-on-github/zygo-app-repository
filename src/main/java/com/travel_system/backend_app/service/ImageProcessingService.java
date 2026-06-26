package com.travel_system.backend_app.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/*
* utiliza 12monkeys para converter imagens para tipos que o ImageIO do JDK não suporta nativamente
* */

@Service
public class ImageProcessingService {

    public byte[] convertImageToWebp(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("[convertImageToWebp] file não pode estar null ou inválido");
        }

        if (!isImage(file)) throw new IllegalArgumentException("[convertImageToWebp] não é uma imagem");

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) throw new IOException("[convertImageToWebp] image null");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            boolean converted = ImageIO.write(image, "webp", outputStream);

            if (!converted) throw new IOException("writer WebP encontrado");

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
