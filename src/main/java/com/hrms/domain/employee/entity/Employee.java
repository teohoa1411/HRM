package com.hrms.domain.employee.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Employee extends BaseCompanyEntity {

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email_work", length = 255)
    private String emailWork;

    @Column(name = "phone_work", length = 20)
    private String phoneWork;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "emergency_phone", length = 20)
    private String emergencyPhone;

    @Column(name = "avatar_s3_key", length = 500)
    private String avatarS3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private EmployeeSourceType sourceType;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "job_role_id", nullable = false)
    private Long jobRoleId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "direct_manager_id")
    private Long directManagerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "employment_type", nullable = false, length = 20)
    @Builder.Default
    private String employmentType = "FULL_TIME";

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private EmployeeDetail detail;
}
