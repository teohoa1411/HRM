package com.hrms.domain.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCompanyRequest(
        @NotBlank(message = "Company code is required")
        @Pattern(regexp = "^[A-Z0-9]{3,20}$", message = "Code must be 3-20 uppercase alphanumeric characters")
        String code,

        @NotBlank(message = "Company name is required")
        String name,

        String shortName,
        String taxCode,
        String businessLicense,
        String address,
        String city,
        String country,
        String phone,

        @Email(message = "Valid email is required")
        String email,

        String website,

        // Admin account info
        @NotBlank(message = "Admin username is required")
        String adminUsername,

        @NotBlank @Email
        String adminEmail
) {}
