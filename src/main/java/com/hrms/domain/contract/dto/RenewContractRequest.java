package com.hrms.domain.contract.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RenewContractRequest(
        /** Loại HĐ mới, có thể khác loại cũ (VD: thử việc → chính thức) */
        @NotNull(message = "Contract type ID is required")
        Long contractTypeId,

        String contractNumber,

        LocalDate signedDate,

        /** Mặc định = ngày hết hạn HĐ cũ + 1 */
        @NotNull(message = "Effective from date is required")
        LocalDate effectiveFrom,

        LocalDate effectiveTo,

        Integer durationMonths,

        Long documentId,

        String notes
) {}
