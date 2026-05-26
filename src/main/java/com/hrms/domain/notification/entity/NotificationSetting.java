package com.hrms.domain.notification.entity;

import com.hrms.domain.common.enums.NotificationTemplateType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "template_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 50)
    private NotificationTemplateType templateType;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Danh sách email nhận, cách nhau bởi dấu phẩy */
    @Column(name = "recipient_emails", columnDefinition = "TEXT")
    private String recipientEmails;

    @Column(name = "days_before_deadline", nullable = false)
    @Builder.Default
    private int daysBeforeDeadline = 7;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
