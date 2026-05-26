package com.hrms.domain.document.service;

import com.hrms.domain.common.enums.DocumentType;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.document.dto.DocumentResponse;
import com.hrms.domain.document.entity.Document;
import com.hrms.domain.document.repository.DocumentRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.infrastructure.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "doc", "docx", "xls", "xlsx"
    );

    /** Map of file extensions to expected magic bytes (first N bytes of file). */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "pdf",  new byte[]{0x25, 0x50, 0x44, 0x46},        // %PDF
            "png",  new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},  // .PNG
            "jpg",  new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
            "jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
            "gif",  new byte[]{0x47, 0x49, 0x46}                // GIF
    );

    private final DocumentRepository documentRepository;
    private final FileStoragePort fileStoragePort;

    @Value("${minio.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

    @Transactional(readOnly = true)
    public List<DocumentResponse> listByEmployee(Long employeeId, Long companyId) {
        return documentRepository.findByEmployeeIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public DocumentResponse uploadDocument(Long employeeId, Long companyId, MultipartFile file, DocumentType documentType) {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String s3Key = String.format("hrms/%d/employees/%d/%s/%s.%s",
                companyId, employeeId, documentType.name().toLowerCase(), UUID.randomUUID(), extension);

        fileStoragePort.upload(file, s3Key);

        Document document = Document.builder()
                .companyId(companyId)
                .employeeId(employeeId)
                .documentType(documentType)
                .fileName(file.getOriginalFilename())
                .s3Key(s3Key)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .version(1)
                .uploadedBy(SecurityUtils.getCurrentUserId())
                .build();

        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public URL getPresignedUrl(Long documentId, Long companyId) {
        Document document = documentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(documentId, companyId)
                .orElseThrow(() -> new NotFoundException("Document", documentId));
        return fileStoragePort.getPresignedUrl(document.getS3Key(), presignedUrlExpiryMinutes);
    }

    @Transactional
    public void deleteDocument(Long documentId, Long companyId) {
        Document document = documentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(documentId, companyId)
                .orElseThrow(() -> new NotFoundException("Document", documentId));

        fileStoragePort.delete(document.getS3Key());
        document.softDelete();
        documentRepository.save(document);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds 50MB limit", HttpStatus.BAD_REQUEST);
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new BusinessException("File type not allowed: " + file.getContentType(), HttpStatus.BAD_REQUEST);
        }

        // Validate file extension
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("File extension not allowed: ." + extension, HttpStatus.BAD_REQUEST);
        }

        // Validate magic bytes for known types (prevents MIME type spoofing)
        byte[] expectedMagic = MAGIC_BYTES.get(extension);
        if (expectedMagic != null) {
            try (InputStream is = file.getInputStream()) {
                byte[] header = new byte[expectedMagic.length];
                if (is.read(header) < expectedMagic.length || !Arrays.equals(header, expectedMagic)) {
                    throw new BusinessException(
                            "File content does not match its extension (." + extension + ")",
                            HttpStatus.BAD_REQUEST);
                }
            } catch (IOException e) {
                throw new BusinessException("Unable to read file for validation", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getEmployeeId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getS3Key(),
                document.getFileSize(),
                document.getMimeType(),
                document.getVersion(),
                document.getUploadedBy(),
                document.getCreatedAt()
        );
    }
}
