package com.hrms.domain.alert.entity;

import com.hrms.domain.common.enums.AlertCategory;
import com.hrms.domain.common.enums.AlertSeverity;
import com.hrms.domain.common.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "alert_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Unique key cho upsert: "{CATEGORY}:{REF_TYPE}:{REF_ID}" */
    @Column(name = "alert_key", nullable = false, length = 100)
    private String alertKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private AlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private AlertSeverity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AlertStatus status = AlertStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "dismissed_by")
    private Long dismissedBy;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /** Helper: tạo alert_key từ components */
    public static String buildKey(AlertCategory category, String refType, Long refId) {
        return category.name() + ":" + refType + ":" + refId;
    }
}
