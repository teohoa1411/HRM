package com.hrms.domain.company.entity;

import com.hrms.domain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Company extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(name = "tax_code", unique = true, length = 20)
    private String taxCode;

    @Column(name = "business_license", length = 100)
    private String businessLicense;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "VN";

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "logo_s3_key", length = 500)
    private String logoS3Key;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
