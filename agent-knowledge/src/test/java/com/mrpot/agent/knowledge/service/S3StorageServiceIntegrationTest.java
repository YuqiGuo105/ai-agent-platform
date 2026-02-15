package com.mrpot.agent.knowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests validating real S3 (Tigris) connectivity.
 * These tests are disabled by default – remove @Disabled or run with specific profile
 * to execute against actual storage.
 */
class S3StorageServiceIntegrationTest {

    private static final String ENDPOINT = "https://t3.storageapi.dev";
    private static final String REGION = "auto";
    private static final String BUCKET = "spacious-trunk-sy3ej1zxqm";
    private static final String ACCESS_KEY = "tid_Esbqocsv_ARWyQrdQbMxAFhYgcwAINzhxBrButwpULfJlPVkbv";
    private static final String SECRET_KEY = "tsec_VgGukJb73Jwyy+VSOvIJdJ5skTbQYx7fHMjZzPI_cUsua87ksgMSQ4+zVjbmoITmtR0Qyw";

    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .endpointOverride(URI.create(ENDPOINT))
                .region(Region.US_EAST_1) // Tigris ignores region, but SDK requires one
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Test
    void testBucketExists() {
        // Head bucket to verify credentials and access
        assertDoesNotThrow(() ->
                s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()));
    }

    @Test
    void testListObjects() {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET)
                .maxKeys(5)
                .build());
        assertNotNull(response);
        System.out.println("Bucket contains " + response.keyCount() + " objects (limited 5)");
    }

    @Test
    void testUploadAndDeleteObject() {
        String key = "test-integration/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".txt";
        byte[] content = "Hello from integration test".getBytes(StandardCharsets.UTF_8);

        // Upload
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromBytes(content)
        );

        // Verify it exists
        ListObjectsV2Response list = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET)
                .prefix(key)
                .build());
        assertEquals(1, list.keyCount());

        // Delete
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .build());

        // Confirm deletion
        ListObjectsV2Response afterDelete = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET)
                .prefix(key)
                .build());
        assertEquals(0, afterDelete.keyCount());
    }
}
