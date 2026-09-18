package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.StorageProperties;
import com.travel_system.backend_app.exceptions.StorageException;
import kotlin.DslMarker;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @InjectMocks
    private S3StorageService s3StorageService;

    @Mock
    private StorageProperties storageProperties;
    @Mock
    private S3Client s3Client;

    @Nested
    class upload {

        @Test
        @DisplayName("Deve realizar o upload com total sucesso, sem interferências")
        void shouldUploadWithSuccess() throws IOException {
            byte[] byteDeclare = {100}; // array de byte
            String objectKey = "exemple_object_key";
            String contentType = "exemple_contentType";
            String bucketName = "bucket_mocked_name";

            when(storageProperties.getBucketName()).thenReturn(bucketName);

            ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            ArgumentCaptor<RequestBody> requestBodyArgumentCaptor = ArgumentCaptor.forClass(RequestBody.class);

            String result = s3StorageService.upload(byteDeclare, objectKey, contentType);

            assertEquals(objectKey, result);

            verify(s3Client, times(1)).putObject(putObjectRequestArgumentCaptor.capture(), requestBodyArgumentCaptor.capture());

            PutObjectRequest capturedRequest = putObjectRequestArgumentCaptor.getValue();
            assertEquals(bucketName, capturedRequest.bucket());
            assertEquals(objectKey, capturedRequest.key());
            assertEquals(contentType, capturedRequest.contentType());
            assertEquals(byteDeclare.length, capturedRequest.contentLength());

            RequestBody capturedBody = requestBodyArgumentCaptor.getValue();
            assertEquals(byteDeclare.length, capturedBody.contentStreamProvider().newStream().available());
        }

        @Test
        @DisplayName("Deve lançar exception quando o storage não responder corretamente")
        void throwExceptionWhenStorageCommunicationFails() {
            byte[] byteDeclare = {100}; // array de byte
            String objectKey = "exemple_object_key";
            String contentType = "exemple_contentType";
            String bucketName = "bucket_mocked_name";

            S3Exception communicationException = mock(S3Exception.class);

            when(storageProperties.getBucketName()).thenReturn(bucketName);

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(communicationException);

            assertThrows(StorageException.class, () -> s3StorageService.upload(byteDeclare, objectKey, contentType));

            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando os parâmetros não forem enviados de forma correta")
        @MethodSource("invalidParametersProvider")
        void throwExceptionWhenParametersAreInvalid(byte[] bytes, String objectKey, String contentType) {
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.upload(bytes, objectKey, contentType));

            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        private static Stream<Arguments> invalidParametersProvider() {
            byte[] bytesArr = {100};
            byte[] bytesArrEqualsZero = {};

            return Stream.of(
                    Arguments.of(null, "objectKeyExemple", "contentTypeExemple"),
                    Arguments.of(bytesArrEqualsZero, "objectKeyExemple", "contentTypeExemple"),

                    Arguments.of(bytesArr, null, "contentTypeExemple"),
                    Arguments.of(bytesArr, "", "contentTypeExemple"),

                    Arguments.of(bytesArr, "objectKeyExemple", null),
                    Arguments.of(bytesArr, "objectKeyExemple", "")
            );
        }
    }

    @Nested
    class delete {

        @Test
        @DisplayName("Deve realizar a deleção com total sucesso, sem interferência alguma")
        void shouldDeleteWithSuccess() {
            String objectKey = "exemple_object_key";
            String bucketName = "bucket_mocked_name";

            when(storageProperties.getBucketName()).thenReturn(bucketName);

            ArgumentCaptor<DeleteObjectRequest> dellObjRequest = ArgumentCaptor.forClass(DeleteObjectRequest.class);

            s3StorageService.delete(objectKey);

            verify(s3Client, times(1)).deleteObject(dellObjRequest.capture());

            DeleteObjectRequest capturedValue = dellObjRequest.getValue();
            assertEquals(bucketName, capturedValue.bucket());
            assertEquals(objectKey, capturedValue.key());
        }

        @Test
        void throwExceptionWhenDeleteObjectFails() {
            String objectKey = "exemple_object_key";
            String bucketName = "bucket_mocked_name";

            S3Exception communicationException = mock(S3Exception.class);

            when(storageProperties.getBucketName()).thenReturn(bucketName);
            when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(communicationException);

            assertThrows(StorageException.class, () -> s3StorageService.delete(objectKey));

            verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        }

        @ParameterizedTest
        @MethodSource("invalidParameterProvider")
        void throwExceptionWhenParameterAreInvalid(String objKey) {
            assertThrows(IllegalArgumentException.class, () -> s3StorageService.delete(objKey));

            verifyNoInteractions(s3Client);
        }

        public static Stream<Arguments> invalidParameterProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("       ")
            );
        }
    }

    @Nested
    class getPublicUrl {

        @Test
        @DisplayName("Deve construir a URL com sucesso")
        void shouldBuildPublicUrlWithSuccess() {
            String objKey = "profiles/iago-123.jpg";
            String publicBaseUrl = "http://localhost:9000/zygo-mocked-teste";

            String expectedPublicBaseUrl = "http://localhost:9000/zygo-mocked-teste/profiles/iago-123.jpg";

            when(storageProperties.getPublicBaseUrl()).thenReturn(publicBaseUrl);

            String publicUrl = s3StorageService.getPublicUrl(objKey);

            System.out.println("publicUrl " + publicUrl);

            assertEquals(expectedPublicBaseUrl, publicUrl);

            verify(storageProperties, times(1)).getPublicBaseUrl();
        }

        @ParameterizedTest
        @DisplayName("Deve retornar silenciosamente quando obj key for null, significando que o user não tem profilePicture")
        @MethodSource("invalidParameterProvider")
        void shouldReturnSilentlyWhenUserWithoutProfilePicture(String objectKey) {
            String publicUrl = s3StorageService.getPublicUrl(objectKey);

            String expectedPublicBaseUrl = "http://localhost:9000/zygo-mocked-teste/profiles/iago-123.jpg";

            assertNotEquals(expectedPublicBaseUrl, publicUrl);
            assertNull(publicUrl);
        }

        public static Stream<Arguments> invalidParameterProvider() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(""),
                    Arguments.of("       ")
            );
        }
    }
}