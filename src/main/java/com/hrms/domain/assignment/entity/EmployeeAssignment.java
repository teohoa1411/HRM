package com.hrms.domain.assignment.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "employee_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class EmployeeAssignment extends BaseCompanyEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "project_code", length = 50)
    private String projectCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Username tại hệ thống khách hàng (VD: ps1.linhdn cho TCB) */
    @Column(name = "client_username", length = 100)
    private String clientUsername;

    /** Văn phòng làm việc (VD: QT, TDH, THD) */
    @Column(name = "work_location", length = 50)
    private String workLocation;
}
