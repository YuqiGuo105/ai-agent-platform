package com.mrpot.agent.knowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.SdkHttpRequest;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3StorageService (mock S3 client).
 */
class S3StorageServiceTest {

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3StorageService service;

    @BeforeEach
    void setUp() throws Exception {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);

        // Create service and inject mocks via reflection
        service = new S3StorageService(s3Client, s3Presigner);
        setField(service, "bucket", "spacious-trunk-sy3ej1zxqm");
        setField(service, "endpoint", "https://t3.storageapi.dev");
        setField(service, "presignTtlSeconds", 900L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = S3StorageService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void presignUpload_shouldReturnPresignedUrl() throws Exception {
        // Mock presigner response
        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        when(mockPresigned.url()).thenReturn(new URL("https://t3.storageapi.dev/bucket/test-key"));
        SdkHttpRequest mockRequest = mock(SdkHttpRequest.class);
        when(mockRequest.method()).thenReturn(software.amazon.awssdk.http.SdkHttpMethod.PUT);
        when(mockPresigned.httpRequest()).thenReturn(mockRequest);

        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        // Execute
        S3StorageService.PresignedUpload result = service.presignUpload("test.pdf", "application/pdf");

        // Verify
        assertNotNull(result);
        assertTrue(result.uploadUrl().contains("https://t3.storageapi.dev"));
        assertNotNull(result.fileUrl());
        assertNotNull(result.objectKey());
        assertEquals("PUT", result.method());
    }

    @Test
    void buildFileUrl_shouldFormatCorrectly() {
        String key = "uploads/123-doc.pdf";
        String url = service.buildFileUrl(key);
        assertEquals("https://t3.storageapi.dev/spacious-trunk-sy3ej1zxqm/uploads/123-doc.pdf", url);
    }

    @Test
    void deleteObject_shouldCallS3Delete() {
        service.deleteObject("uploads/test-key");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("spacious-trunk-sy3ej1zxqm", captor.getValue().bucket());
        assertEquals("uploads/test-key", captor.getValue().key());
    }

    @Test
    void deleteByUrl_shouldExtractKeyAndDelete() {
        String fileUrl = "https://t3.storageapi.dev/spacious-trunk-sy3ej1zxqm/uploads/test-file.pdf";
        service.deleteByUrl(fileUrl);

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteByUrl_shouldSkipInvalidUrl() {
        service.deleteByUrl("https://invalid-url.com/whatever");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void uploadObject_shouldCallS3Put() {
        byte[] data = "test data".getBytes();
        String key = "uploads/test-put.txt";

        service.uploadObject(key, data, "text/plain");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("spacious-trunk-sy3ej1zxqm", captor.getValue().bucket());
        assertEquals(key, captor.getValue().key());
        assertEquals("text/plain", captor.getValue().contentType());
    }

    @Test
    void getBucket_shouldReturnConfiguredBucket() {
        assertEquals("spacious-trunk-sy3ej1zxqm", service.getBucket());
    }
}
