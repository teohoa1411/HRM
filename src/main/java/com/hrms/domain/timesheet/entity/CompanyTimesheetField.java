package com.hrms.domain.timesheet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_timesheet_fields",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "field_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyTimesheetField {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    @Column(name = "field_type", nullable = false, length = 20)
    @Builder.Default
    private String fieldType = "text"; // text | number | checkbox

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
