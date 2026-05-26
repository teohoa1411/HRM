package com.hrms.domain.vendor.dto;

import com.hrms.domain.common.enums.VendorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateVendorRequest(
        @NotBlank(message = "Vendor code is required")
        String code,

        @NotBlank(message = "Vendor name is required")
        String name,

        @NotNull(message = "Vendor type is required")
        VendorType vendorType,

        String contactPerson,
        String contactEmail,
        String contactPhone,
        String address,
        String taxCode,
        String notes,

        // Auto-create VENDOR_USER account
        @NotBlank(message = "Account username is required")
        String accountUsername,

        @NotBlank(message = "Account email is required")
        String accountEmail
) {}
