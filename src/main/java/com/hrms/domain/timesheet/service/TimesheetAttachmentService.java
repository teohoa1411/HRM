package com.hrms.domain.timesheet.service;

import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.timesheet.dto.TimesheetAttachmentResponse;
import com.hrms.domain.timesheet.entity.TimesheetAttachment;
import com.hrms.domain.timesheet.repository.TimesheetAttachmentRepository;
import com.hrms.infrastructure.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimesheetAttachmentService {

    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 20 * 1024 * 1024L;

    private final TimesheetAttachmentRepository repo;
    private final FileStoragePort storage;

    @Value("${minio.presigned-url-expiry-minutes:15}")
    private int expiryMinutes;

    public List<TimesheetAttachmentResponse> list(Long userId, String yearMonth) {
        return repo.findByUserIdAndYearMonthOrderByCreatedAtDesc(userId, yearMonth)
                   .stream().map(TimesheetAttachmentResponse::from).toList();
    }

    @Transactional
    public TimesheetAttachmentResponse upload(Long userId, Long companyId,
                                               String yearMonth, MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new BusinessException("File is empty", HttpStatus.BAD_REQUEST);
        if (file.getSize() > MAX_SIZE)
            throw new BusinessException("File exceeds 20MB limit", HttpStatus.BAD_REQUEST);
        if (!ALLOWED.contains(file.getContentType()))
            throw new BusinessException("Only PDF and images allowed", HttpStatus.BAD_REQUEST);

        String ext = ext(file.getOriginalFilename());
        String key = String.format("hrms/%d/timesheets/%d/%s/%s.%s",
                companyId, userId, yearMonth, UUID.randomUUID(), ext);

        storage.upload(file, key);

        TimesheetAttachment a = TimesheetAttachment.builder()
                .userId(userId).companyId(companyId).yearMonth(yearMonth)
                .fileName(file.getOriginalFilename()).s3Key(key)
                .fileSize(file.getSize()).mimeType(file.getContentType())
                .createdBy(userId).build();

        return TimesheetAttachmentResponse.from(repo.save(a));
    }

    public URL getUrl(Long id, Long companyId, Long userId) {
        TimesheetAttachment a = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        if (!a.getCreatedBy().equals(userId)) {
            throw new BusinessException("Không có quyền truy cập attachment của người khác",
                    HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        return storage.getPresignedUrl(a.getS3Key(), expiryMinutes);
    }

    /** Admin: lấy URL không cần kiểm tra owner. */
    public URL getUrlAdmin(Long id, Long companyId) {
        TimesheetAttachment a = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        return storage.getPresignedUrl(a.getS3Key(), expiryMinutes);
    }

    @Transactional
    public void delete(Long id, Long companyId, Long userId) {
        TimesheetAttachment a = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        if (!a.getCreatedBy().equals(userId)) {
            throw new BusinessException("Không có quyền xóa attachment của người khác",
                    HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        storage.delete(a.getS3Key());
        repo.delete(a);
    }

    /** Admin: xóa attachment không cần kiểm tra owner. */
    @Transactional
    public void deleteAdmin(Long id, Long companyId) {
        TimesheetAttachment a = repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        storage.delete(a.getS3Key());
        repo.delete(a);
    }

    private String ext(String name) {
        if (name == null || !name.contains(".")) return "bin";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }
}
