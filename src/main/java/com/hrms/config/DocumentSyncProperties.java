package com.hrms.config;

import com.hrms.domain.common.enums.SyncTargetType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "document-sync")
public class DocumentSyncProperties {

    /** Bật/tắt tính năng đồng bộ */
    private boolean enabled = true;

    /** Loại đích: LOCAL hoặc S3_BUCKET */
    private SyncTargetType targetType = SyncTargetType.LOCAL;

    /** Đường dẫn thư mục đích (LOCAL) hoặc s3://bucket/prefix (S3_BUCKET) */
    private String targetPath = "/data/hrms-sync/";

    /** Tên bucket backup khi targetType = S3_BUCKET */
    private String backupBucket = "hrms-backup";
}
