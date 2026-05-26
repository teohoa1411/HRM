package com.hrms.domain.statement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "statements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "statement_type", "recipient_id", "statement_month"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Statement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** INTERNAL | CUSTOMER | VENDOR */
    @Column(name = "statement_type", nullable = false, length = 10)
    private String statementType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "statement_month", nullable = false, length = 7)
    private String statementMonth;

    @Column(name = "total_employees", nullable = false)
    @Builder.Default
    private int totalEmployees = 0;

    @Column(name = "total_days_worked", nullable = false, precision = 7, scale = 1)
    @Builder.Default
    private BigDecimal totalDaysWorked = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    /**
     * Trạng thái biên bản:
     * <ul>
     *   <li>DRAFT      — đang soạn, chưa gửi</li>
     *   <li>EXPORTED   — đã xuất file (PDF/Excel), chưa gửi cho đối tác</li>
     *   <li>SENT       — đã gửi cho khách hàng / vendor</li>
     *   <li>CONFIRMED  — đối tác đã xác nhận</li>
     *   <li>STALE      — timesheet bị chỉnh sau khi xuất → cần tái tạo</li>
     * </ul>
     */
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "file_s3_key", length = 500)
    private String fileS3Key;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "sent_by")
    private Long sentBy;

    /** Thời điểm đối tác xác nhận biên bản */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** User ID người xác nhận (hoặc người nhập xác nhận từ email) */
    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    /** Thời điểm xuất file lần cuối cùng */
    @Column(name = "exported_at")
    private LocalDateTime exportedAt;

    /**
     * Lý do trạng thái STALE — mô tả timesheet nào bị chỉnh và khi nào.
     * Ví dụ: "Timesheet của Nguyễn Văn A ngày 2025-05-15 bị chỉnh lúc 09:30"
     */
    @Column(name = "stale_reason", length = 500)
    private String staleReason;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StatementLineItem> lineItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Business helpers ──────────────────────────────────────────────────────

    /** Biên bản đã được xuất hay chưa (ở bất kỳ trạng thái nào sau DRAFT) */
    public boolean isExported() {
        return "EXPORTED".equals(status) || "SENT".equals(status) || "CONFIRMED".equals(status) || "CLOSED".equals(status);
    }

    /** Biên bản đã bị stale (timesheet bị chỉnh sau khi xuất) */
    public boolean isStale() {
        return "STALE".equals(status);
    }

    /** Biên bản đã được gửi cho đối tác */
    public boolean isSent() {
        return "SENT".equals(status) || "CONFIRMED".equals(status);
    }

    /** Chỉ cho phép chỉnh sửa khi còn ở DRAFT hoặc STALE */
    public boolean isEditable() {
        return "DRAFT".equals(status) || "STALE".equals(status);
    }

    /**
     * Đánh dấu STALE khi timesheet bị chỉnh sau khi đã xuất.
     *
     * @param reason mô tả nguyên nhân (nhân sự nào, ngày nào bị chỉnh)
     */
    public void markStale(String reason) {
        this.status = "STALE";
        this.staleReason = reason;
    }

    /**
     * Đánh dấu đã xuất file.
     *
     * @param s3Key   đường dẫn file trên S3
     * @param exportedBy user ID người xuất
     */
    public void markExported(String s3Key, Long exportedBy) {
        this.status = "EXPORTED";
        this.fileS3Key = s3Key;
        this.exportedAt = LocalDateTime.now();
        this.staleReason = null;
    }

    /**
     * Đánh dấu đã gửi cho đối tác.
     *
     * @param sentByUserId user ID người gửi
     */
    public void markSent(Long sentByUserId) {
        this.status = "SENT";
        this.sentAt = LocalDateTime.now();
        this.sentBy = sentByUserId;
    }

    /**
     * Đánh dấu đối tác đã xác nhận.
     *
     * @param confirmedByUserId user ID người nhập xác nhận
     */
    public void markConfirmed(Long confirmedByUserId) {
        this.status = "CONFIRMED";
        this.confirmedAt = LocalDateTime.now();
        this.confirmedBy = confirmedByUserId;
    }

    /**
     * Reset về DRAFT để tái tạo biên bản sau khi STALE.
     * Xoá file cũ và reset các thông tin gửi.
     */
    public void resetToDraft() {
        this.status = "DRAFT";
        this.fileS3Key = null;
        this.sentAt = null;
        this.sentBy = null;
        this.confirmedAt = null;
        this.confirmedBy = null;
        this.exportedAt = null;
        this.staleReason = null;
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    public void markClosed(Long closedByUserId) {
        this.status = "CLOSED";
        this.closedAt = LocalDateTime.now();
        this.closedBy = closedByUserId;
    }

    public void reopen() {
        this.status = "CONFIRMED";
        this.closedAt = null;
        this.closedBy = null;
    }
}
