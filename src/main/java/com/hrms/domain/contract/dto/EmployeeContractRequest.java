package com.hrms.domain.contract.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EmployeeContractRequest(
        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Contract type ID is required")
        Long contractTypeId,

        String contractNumber,

        LocalDate signedDate,

        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        /** null = vô thời hạn hoặc cam kết vĩnh viễn */
        LocalDate effectiveTo,

        /** Thời hạn tháng, nếu nhập thì tự tính effectiveTo */
        Integer durationMonths,

        /** FK sang Document (file scan đã upload), nullable */
        Long documentId,

        String notes
) {}
