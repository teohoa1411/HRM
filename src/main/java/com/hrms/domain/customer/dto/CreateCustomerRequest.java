package com.hrms.domain.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 30)
        String code,

        /** Mã vendor do khách hàng cấp cho công ty mình, xuất trong biên bản KH. */
        @Size(max = 50)
        String vendorCode,

        @NotBlank(message = "Name is required")
        @Size(max = 255)
        String name,

        String contactPerson,
        String contactEmail,
        String contactPhone,
        String address,
        String taxCode,
        String notes
) {}
