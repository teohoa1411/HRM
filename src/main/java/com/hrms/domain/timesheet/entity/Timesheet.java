package com.hrms.domain.timesheet.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "timesheets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_key", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Timesheet extends BaseCompanyEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_key", nullable = false, length = 50)
    private String projectKey;

    @Column(name = "project_id", length = 50)
    private String projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false, precision = 4, scale = 1)
    private BigDecimal hours;

    @Column(name = "ot_hours", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal otHours = BigDecimal.ZERO;

    @Column(name = "note", length = 255)
    private String note;

    /**
     * true = entry này được tạo tự động bởi hệ thống khi leave được duyệt.
     * Nhân sự không được phép chỉnh sửa; chỉ quản lý mới được sửa.
     */
    @Column(name = "locked_by_leave", nullable = false)
    @Builder.Default
    private boolean lockedByLeave = false;
}
