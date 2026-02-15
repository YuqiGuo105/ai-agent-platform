package com.mrpot.agent.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${storage.s3.bucket}")
    private String bucket;

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    @Value("${storage.s3.presign-ttl-seconds:900}")
    private long presignTtlSeconds;

    public PresignedUpload presignUpload(String originalFilename, String contentType) {
        String safeName = originalFilename == null ? "upload" : originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
        String objectKey = "uploads/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(60, presignTtlSeconds)))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String fileUrl = buildFileUrl(objectKey);
        return new PresignedUpload(presigned.url().toString(), fileUrl, objectKey, presigned.httpRequest().method().name());
    }

    /**
     * Generate a presigned GET URL for downloading a private S3 object.
     */
    public PresignedDownload presignDownload(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(60, presignTtlSeconds)))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new PresignedDownload(presigned.url().toString(), objectKey, presignTtlSeconds);
    }

    /**
     * Generate a presigned GET URL from a file URL (extracts key automatically).
     */
    public PresignedDownload presignDownloadByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            throw new IllegalArgumentException("Cannot extract object key from URL: " + fileUrl);
        }
        return presignDownload(key);
    }

    public byte[] download(String objectKey) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build()).asByteArray();
    }

    public void deleteByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            log.warn("Cannot delete S3 object, failed to parse key from URL: {}", fileUrl);
            return;
        }
        deleteObject(key);
    }

    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("Deleted S3 object: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", key, e.getMessage());
        }
    }

    public void uploadObject(String key, byte[] data, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    public String buildFileUrl(String objectKey) {
        String trimmed = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return trimmed + "/" + bucket + "/" + objectKey;
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(bucket)) {
            return null;
        }
        int idx = fileUrl.indexOf(bucket);
        if (idx < 0) {
            return null;
        }
        return fileUrl.substring(idx + bucket.length() + 1);
    }

    public record PresignedUpload(String uploadUrl, String fileUrl, String objectKey, String method) {}

    public record PresignedDownload(String downloadUrl, String objectKey, long expiresInSeconds) {}

    public String getBucket() {
        return bucket;
    }
}
