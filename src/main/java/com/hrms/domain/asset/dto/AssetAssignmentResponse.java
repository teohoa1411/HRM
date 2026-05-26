package com.hrms.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetAssignmentResponse(
    Long id,
    Long assetId,
    String assetCode,
    String assetName,
    String serialNumber,
    Long employeeId,
    String employeeName,
    String employeeCode,
    LocalDate assignedDate,
    LocalDate returnedDate,
    Long assignedBy,
    String assignedByName,
    String conditionOnAssign,
    String conditionOnReturn,
    BigDecimal monthlyRentalPrice,
    String notes
) {}
