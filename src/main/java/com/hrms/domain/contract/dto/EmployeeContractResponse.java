package com.hrms.domain.contract.dto;

import com.hrms.domain.common.enums.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeContractResponse(
        Long id,
        String publicId,
        Long employeeId,
        String employeePublicId,
        String employeeName,
        String employeeCode,
        Long contractTypeId,
        String contractTypeName,
        String contractTypeCategory,
        String contractNumber,
        LocalDate signedDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer durationMonths,
        ContractStatus status,
        Long documentId,
        String notes,
        LocalDateTime createdAt
) {}
