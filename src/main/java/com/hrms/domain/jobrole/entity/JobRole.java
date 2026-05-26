package com.hrms.domain.jobrole.entity;

import com.hrms.domain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "job_roles",
       uniqueConstraints = @UniqueConstraint(name = "uq_job_role_code_company",
               columnNames = {"company_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class JobRole extends BaseEntity {

    /** Công ty sở hữu job role này — mỗi công ty tự quản lý */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "seniority_level", nullable = false, length = 20)
    @Builder.Default
    private String seniorityLevel = "MIDDLE";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "skills_required", columnDefinition = "JSON")
    private String skillsRequired;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
