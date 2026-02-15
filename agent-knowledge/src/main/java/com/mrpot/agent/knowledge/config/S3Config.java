package com.mrpot.agent.knowledge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${storage.s3.endpoint}") String endpoint,
            @Value("${storage.s3.region:auto}") String region,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey,
            @Value("${storage.s3.path-style:true}") boolean pathStyle
    ) {
        Region resolvedRegion = resolveRegion(region);
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .region(resolvedRegion)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${storage.s3.endpoint}") String endpoint,
            @Value("${storage.s3.region:auto}") String region,
            @Value("${storage.s3.access-key}") String accessKey,
            @Value("${storage.s3.secret-key}") String secretKey,
            @Value("${storage.s3.path-style:true}") boolean pathStyle
    ) {
        Region resolvedRegion = resolveRegion(region);
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .region(resolvedRegion)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .build();
    }

    private Region resolveRegion(String rawRegion) {
        String normalized = rawRegion == null ? "" : rawRegion.trim();
        if (normalized.isBlank() || "auto".equalsIgnoreCase(normalized)) {
            return Region.US_EAST_1; // Tigris accepts any AWS-like region; default to us-east-1
        }
        return Region.of(normalized);
    }
}
