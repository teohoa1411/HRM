package com.hrms.domain.contract.dto;

import com.hrms.domain.common.enums.ContractStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO gom nhóm hợp đồng theo nhân viên.
 * Mỗi record = 1 nhân viên + tổng hợp thông tin HĐ.
 */
public record EmployeeContractGroupDto(
        Long employeeId,
        String employeeName,
        String employeeCode,

        /** Tổng số HĐ (cả CONTRACT + COMMITMENT) */
        int contractCount,

        /** Số HĐ đang ACTIVE */
        int activeContractCount,

        /** Tên các loại HĐ đã ký (unique, dùng cho chips) */
        List<String> contractTypeNames,

        /** Tên loại HĐ lao động hiện tại (CONTRACT category, ACTIVE, mới nhất) */
        String currentContractTypeName,

        /** Trạng thái HĐ hiện tại */
        ContractStatus currentContractStatus,

        /** Ngày hết hạn HĐ hiện tại (null = vô thời hạn) */
        LocalDate currentEffectiveTo,

        /** Trạng thái xấu nhất: EXPIRED > EXPIRING_SOON > ACTIVE > TERMINATED */
        ContractStatus worstStatus,

        /** Số ngày còn lại (null = vô thời hạn, âm = quá hạn) */
        Long daysUntilExpiry,

        /** Danh sách HĐ chi tiết (sort effectiveFrom DESC) */
        List<EmployeeContractResponse> contracts
) {}
