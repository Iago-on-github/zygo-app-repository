package com.travel_system.backend_app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageProcessingServiceTest {

    @InjectMocks
    private ImageProcessingService imgProcessingService;

    @Nested
    class convertImageToJPEG {

        @Nested
        class successScenarios {
            @Test
            @DisplayName("Deve converter a imagem de entrada PNG válida para o tipo JPEG com sucesso")
            void shouldConvertPngImageToJpegWithSuccess() throws IOException {
                int larguraOriginal = 10;
                int alturaOriginal = 10;

                BufferedImage pngMockImage = new BufferedImage(larguraOriginal, alturaOriginal, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = pngMockImage.createGraphics();
                g2d.setColor(Color.RED);
                g2d.fillRect(0, 0, larguraOriginal, alturaOriginal);
                g2d.dispose();

                ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                ImageIO.write(pngMockImage, "png", pngOutputStream);
                byte[] pngBytes = pngOutputStream.toByteArray();

                MultipartFile multipartFile = new MockMultipartFile(
                        "file",
                        "foto_teste.png",
                        "image/png",
                        pngBytes
                );

                byte[] resultadoJpegBytes = imgProcessingService.convertImageToJPEG(multipartFile);

                assertNotNull(resultadoJpegBytes, "O array de bytes retornado não deveria ser nulo");
                assertTrue(resultadoJpegBytes.length > 0, "O arquivo JPEG gerado não deveria estar vazio");

                ByteArrayInputStream jpegInputStream = new ByteArrayInputStream(resultadoJpegBytes);
                BufferedImage resultadoImage = ImageIO.read(jpegInputStream);

                System.out.println("teste" + resultadoImage);

                assertNotNull(resultadoImage, "O ImageIO deveria conseguir ler o array de bytes gerado como uma imagem válida");
                assertEquals(larguraOriginal, resultadoImage.getWidth(), "A largura da imagem convertida deve ser idêntica à original");
                assertEquals(alturaOriginal, resultadoImage.getHeight(), "A altura da imagem convertida deve ser idêntica à original");

                assertFalse(resultadoImage.getColorModel().hasAlpha(), "A imagem JPEG final não deve conter canal de transparência (Alpha)");
                assertEquals(3, resultadoImage.getColorModel().getNumComponents(), "A imagem deve conter exatamente 3 componentes de cor (RGB)");        }

            @Test
            @DisplayName("Deve converter uma imagem WEBP válida para JPEG com sucesso")
            void shouldConvertWebpImageToJpegWithSuccess() throws IOException {
                int larguraOriginal = 20;
                int alturaOriginal = 20;

                BufferedImage webpMockImage = new BufferedImage(larguraOriginal, alturaOriginal, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = webpMockImage.createGraphics();
                g2d.setColor(Color.BLUE);
                g2d.fillRect(0, 0, larguraOriginal, alturaOriginal);
                g2d.dispose();

                ByteArrayOutputStream webpOutputStream = new ByteArrayOutputStream();

                boolean writeSuccess = ImageIO.write(webpMockImage, "webp", webpOutputStream);

                if (!writeSuccess) {
                    ImageIO.write(webpMockImage, "png", webpOutputStream);
                }

                byte[] webpBytes = webpOutputStream.toByteArray();

                MultipartFile multipartFile = new MockMultipartFile(
                        "file",
                        "foto_perfil.webp",
                        "image/webp",
                        webpBytes
                );

                byte[] resultadoJpegBytes = imgProcessingService.convertImageToJPEG(multipartFile);

                assertNotNull(resultadoJpegBytes, "O array de bytes retornado não deveria ser nulo");
                assertTrue(resultadoJpegBytes.length > 0, "O arquivo JPEG gerado não deveria estar vazio");

                ByteArrayInputStream jpegInputStream = new ByteArrayInputStream(resultadoJpegBytes);
                BufferedImage resultadoImage = ImageIO.read(jpegInputStream);

                assertNotNull(resultadoImage, "O ImageIO deveria conseguir decodificar o resultado como uma imagem válida");
                assertEquals(larguraOriginal, resultadoImage.getWidth(), "A largura deve ser mantida idêntica à do WEBP original");
                assertEquals(alturaOriginal, resultadoImage.getHeight(), "A altura deve ser mantida idêntica à do WEBP original");

                assertFalse(resultadoImage.getColorModel().hasAlpha(), "A imagem JPEG resultante não pode conter canal Alpha (transparência)");
                assertEquals(3, resultadoImage.getColorModel().getNumComponents(), "A imagem convertida deve possuir exatamente os 3 canais de cor RGB");
            }

            @Test
            @DisplayName("Deve converter imagem com transparência preenchendo o fundo de branco com sucesso")
            void shouldConvertTransparentImageKeepingWhiteBackground() throws IOException {
                int larguraOriginal = 5;
                int alturaOriginal = 5;

                BufferedImage pngTransparentImage = new BufferedImage(larguraOriginal, alturaOriginal, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = pngTransparentImage.createGraphics();
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, larguraOriginal, alturaOriginal);
                g2d.dispose();

                ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                ImageIO.write(pngTransparentImage, "png", pngOutputStream);
                byte[] pngBytes = pngOutputStream.toByteArray();

                MultipartFile multipartFile = new MockMultipartFile(
                        "file",
                        "transparente.png",
                        "image/png",
                        pngBytes
                );

                byte[] resultadoJpegBytes = imgProcessingService.convertImageToJPEG(multipartFile);

                assertNotNull(resultadoJpegBytes);
                assertTrue(resultadoJpegBytes.length > 0);

                ByteArrayInputStream jpegInputStream = new ByteArrayInputStream(resultadoJpegBytes);
                BufferedImage resultadoImage = ImageIO.read(jpegInputStream);

                assertNotNull(resultadoImage);
                assertEquals(larguraOriginal, resultadoImage.getWidth());
                assertEquals(alturaOriginal, resultadoImage.getHeight());
                assertFalse(resultadoImage.getColorModel().hasAlpha());
                assertEquals(3, resultadoImage.getColorModel().getNumComponents());

                int pixelCentro = resultadoImage.getRGB(larguraOriginal / 2, alturaOriginal / 2);
                Color corCentro = new Color(pixelCentro, false);

                assertEquals(255, corCentro.getRed());
                assertEquals(255, corCentro.getGreen());
                assertEquals(255, corCentro.getBlue());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar UnsupportedMediaTypeException quando o arquivo contiver dados corrompidos ou inválidos")
            void shouldThrowUnsupportedMediaTypeExceptionWhenFileDataIsCorrupted() {
                byte[] corruptedBytes = {0, 1, 2, 3, 4, 5};

                MultipartFile multipartFile = new MockMultipartFile(
                        "file",
                        "corrompida.png",
                        "image/png",
                        corruptedBytes
                );

                UnsupportedMediaTypeException exception = assertThrows(
                        UnsupportedMediaTypeException.class,
                        () -> imgProcessingService.convertImageToJPEG(multipartFile)
                );

                assertEquals("[convertImageToJPEG] originalImage null ou inválida", exception.getMessage());
            }

            @Test
            @DisplayName("Deve propagar IOException quando ocorrer um erro de I/O ao obter o input stream do arquivo")
            void shouldPropagateIOExceptionWhenGetInputStreamFails() throws IOException {
                MultipartFile multipartFile = mock(MultipartFile.class);

                when(multipartFile.isEmpty()).thenReturn(false);
                when(multipartFile.getContentType()).thenReturn("image/png");
                when(multipartFile.getInputStream()).thenThrow(new IOException("Disk error or stream closed"));

                assertThrows(
                        IOException.class,
                        () -> imgProcessingService.convertImageToJPEG(multipartFile)
                );

                verify(multipartFile, times(1)).getInputStream();
            }

            @ParameterizedTest
            @MethodSource("invalidsParametersProvider")
            @DisplayName("Deve lançar IllegalArgumentException quando o arquivo for nulo ou vazio")
            void throwExceptionWhenParametersAreInvalid(MultipartFile multipartFile) {
                assertThrows(IllegalArgumentException.class, () -> imgProcessingService.convertImageToJPEG(multipartFile));
            }

            public static Stream<Arguments> invalidsParametersProvider() {
                MultipartFile emptyMultipartFile = mock(MultipartFile.class);
                when(emptyMultipartFile.isEmpty()).thenReturn(true);

                return Stream.of(
                        Arguments.of((Object) null),
                        Arguments.of(emptyMultipartFile)
                );
            }
        }
    }
}