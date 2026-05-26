package com.hrms.domain.contract.entity;

import com.hrms.domain.common.enums.ContractCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_types",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ContractCategory category;

    /** Loại HĐ này có thời hạn hay không (cam kết vĩnh viễn thì false) */
    @Column(name = "has_expiry", nullable = false)
    @Builder.Default
    private boolean hasExpiry = true;

    /** Thời hạn mặc định (tháng), null nếu không áp dụng */
    @Column(name = "default_duration_months")
    private Integer defaultDurationMonths;

    /** Nhắc trước bao nhiêu ngày khi sắp hết hạn */
    @Column(name = "reminder_days_before", nullable = false)
    @Builder.Default
    private int reminderDaysBefore = 30;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
