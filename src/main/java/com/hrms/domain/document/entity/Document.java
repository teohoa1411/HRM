package com.hrms.domain.document.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Document extends BaseCompanyEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "onedrive_item_id", length = 500)
    private String onedriveItemId;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
}
