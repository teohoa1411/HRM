package com.hrms.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

@Component
@Slf4j
public class S3StorageAdapter implements FileStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3StorageAdapter(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${minio.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(MultipartFile file, String key) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded file to S3: {}", key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        log.info("Deleted file from S3: {}", key);
    }

    @Override
    public URL getPresignedUrl(String key, int expiryMinutes) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(r -> r.bucket(bucketName).key(key))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url();
    }

    @Override
    public InputStream download(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObject(request);
    }

    @Override
    public void copyObject(String sourceKey, String destBucket, String destKey) {
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(destBucket)
                .destinationKey(destKey)
                .build();
        s3Client.copyObject(request);
        log.info("Copied S3 object {} -> {}/{}", sourceKey, destBucket, destKey);
    }

    @Override
    public String getETag(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        HeadObjectResponse response = s3Client.headObject(request);
        // ETag thường có dấu ngoặc kép, bỏ đi để dùng làm checksum
        String etag = response.eTag();
        return etag != null ? etag.replace("\"", "") : null;
    }
}
