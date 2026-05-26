package com.hrms.domain.customer.entity;

import com.hrms.domain.common.entity.BaseCompanyEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Customer extends BaseCompanyEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Mã vendor do khách hàng cấp cho công ty mình — dùng in lên biên bản gửi KH. */
    @Column(name = "vendor_code", length = 50)
    private String vendorCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
