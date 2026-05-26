package com.hrms.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;

public interface FileStoragePort {

    String upload(MultipartFile file, String key);

    void delete(String key);

    URL getPresignedUrl(String key, int expiryMinutes);

    /**
     * Download nội dung file từ S3/MinIO về dạng stream.
     * Caller phải close InputStream sau khi dùng xong.
     */
    InputStream download(String key);

    /**
     * Copy object trong cùng bucket hoặc sang bucket khác (S3_BUCKET target).
     * @param sourceKey key nguồn
     * @param destBucket bucket đích
     * @param destKey    key đích
     */
    void copyObject(String sourceKey, String destBucket, String destKey);

    /**
     * Lấy ETag của object (MD5 cho file < 5 GB) để phát hiện thay đổi.
     */
    String getETag(String key);
}
