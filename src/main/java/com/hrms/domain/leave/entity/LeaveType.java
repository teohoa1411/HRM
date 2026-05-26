package com.hrms.domain.leave.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_types",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private boolean paid = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** TRUE = tự động cấp quota theo tỷ lệ tháng khi tạo nhân sự INTERNAL */
    @Column(name = "auto_grant_on_create", nullable = false)
    @Builder.Default
    private boolean autoGrantOnCreate = false;

    /** Cách làm tròn quota: FLOOR (xuống), CEIL (lên), HALF_UP (>=0.5 làm tròn lên) */
    @Column(name = "grant_rounding", nullable = false, length = 10)
    @Builder.Default
    private String grantRounding = "HALF_UP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
