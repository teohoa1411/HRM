package com.hrms.domain.contract.service;

import com.hrms.domain.common.enums.ContractStatus;
import com.hrms.domain.common.event.ContractCreatedEvent;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.contract.dto.EmployeeContractGroupDto;
import com.hrms.domain.contract.dto.EmployeeContractRequest;
import com.hrms.domain.contract.dto.EmployeeContractResponse;
import com.hrms.domain.contract.dto.ExtendContractRequest;
import com.hrms.domain.contract.dto.RenewContractRequest;
import com.hrms.domain.contract.entity.ContractType;
import com.hrms.domain.contract.entity.EmployeeContract;
import com.hrms.domain.contract.repository.ContractTypeRepository;
import com.hrms.domain.contract.repository.EmployeeContractRepository;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeContractService {

    private final EmployeeContractRepository contractRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── Queries ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<EmployeeContractResponse> list(Long companyId, Long contractTypeId, Pageable pageable) {
        Page<EmployeeContract> page = contractTypeId != null
                ? contractRepository.findByCompanyIdAndContractTypeIdAndDeletedAtIsNull(companyId, contractTypeId, pageable)
                : contractRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);
        return page.map(c -> enrich(c, companyId));
    }

    @Transactional(readOnly = true)
    public EmployeeContractResponse getByPublicId(String publicId, Long companyId) {
        EmployeeContract c = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));
        return enrich(c, companyId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeContractResponse> getByEmployee(Long employeeId, Long companyId) {
        return contractRepository.findByEmployeeIdAndCompanyIdAndDeletedAtIsNullOrderByEffectiveFromDesc(employeeId, companyId)
                .stream().map(c -> enrich(c, companyId)).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeContractResponse> getExpiring(Long companyId, int withinDays) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(withinDays);
        return contractRepository.findExpiringSoon(companyId, from, to)
                .stream().map(c -> enrich(c, companyId)).toList();
    }

    // ── Commands ─────────────────────────────────────────

    @Transactional
    public EmployeeContractResponse create(EmployeeContractRequest request, Long companyId) {
        // Validate employee belongs to company
        Employee employee = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.employeeId(), companyId)
                .orElseThrow(() -> new NotFoundException("Employee", request.employeeId()));

        // Validate contract type belongs to company
        ContractType contractType = contractTypeRepository.findByIdAndCompanyId(request.contractTypeId(), companyId)
                .orElseThrow(() -> new NotFoundException("ContractType", request.contractTypeId()));

        // Calculate effectiveTo from durationMonths if provided
        LocalDate effectiveTo = request.effectiveTo();
        Integer durationMonths = request.durationMonths();
        if (effectiveTo == null && durationMonths != null && durationMonths > 0) {
            effectiveTo = request.effectiveFrom().plusMonths(durationMonths);
        }
        // Nếu loại HĐ không có thời hạn thì effectiveTo = null
        if (!contractType.isHasExpiry()) {
            effectiveTo = null;
            durationMonths = null;
        }

        EmployeeContract contract = EmployeeContract.builder()
                .companyId(companyId)
                .employeeId(request.employeeId())
                .contractTypeId(request.contractTypeId())
                .contractNumber(request.contractNumber())
                .signedDate(request.signedDate())
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(effectiveTo)
                .durationMonths(durationMonths)
                .documentId(request.documentId())
                .notes(request.notes())
                .status(ContractStatus.ACTIVE)
                .build();

        EmployeeContractResponse response = enrich(contractRepository.save(contract), companyId);

        // Publish event → InsuranceEventListener sẽ tự động tạo báo tăng BH nếu là HĐ chính thức
        eventPublisher.publishEvent(new ContractCreatedEvent(
                this, companyId, request.employeeId(),
                contractType.getId(), contractType.getCode(),
                request.effectiveFrom()));

        return response;
    }

    @Transactional
    public EmployeeContractResponse update(String publicId, EmployeeContractRequest request, Long companyId) {
        EmployeeContract contract = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));

        ContractType contractType = contractTypeRepository.findByIdAndCompanyId(request.contractTypeId(), companyId)
                .orElseThrow(() -> new NotFoundException("ContractType", request.contractTypeId()));

        LocalDate effectiveTo = request.effectiveTo();
        Integer durationMonths = request.durationMonths();
        if (effectiveTo == null && durationMonths != null && durationMonths > 0) {
            effectiveTo = request.effectiveFrom().plusMonths(durationMonths);
        }
        if (!contractType.isHasExpiry()) {
            effectiveTo = null;
            durationMonths = null;
        }

        contract.setContractTypeId(request.contractTypeId());
        contract.setContractNumber(request.contractNumber());
        contract.setSignedDate(request.signedDate());
        contract.setEffectiveFrom(request.effectiveFrom());
        contract.setEffectiveTo(effectiveTo);
        contract.setDurationMonths(durationMonths);
        contract.setDocumentId(request.documentId());
        contract.setNotes(request.notes());

        return enrich(contractRepository.save(contract), companyId);
    }

    /**
     * Gia hạn HĐ hiện tại — giữ nguyên bản ghi cũ, chỉ kéo dài effectiveTo.
     */
    @Transactional
    public EmployeeContractResponse extend(String publicId, ExtendContractRequest request, Long companyId) {
        EmployeeContract contract = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRING_SOON) {
            throw new BusinessException("Chỉ có thể gia hạn hợp đồng đang hiệu lực hoặc sắp hết hạn", HttpStatus.BAD_REQUEST);
        }
        if (contract.getEffectiveTo() == null) {
            throw new BusinessException("Hợp đồng vô thời hạn không cần gia hạn", HttpStatus.BAD_REQUEST);
        }

        // Kéo dài thêm tháng
        LocalDate newEffectiveTo = contract.getEffectiveTo().plusMonths(request.additionalMonths());
        int newDuration = (contract.getDurationMonths() != null ? contract.getDurationMonths() : 0) + request.additionalMonths();

        contract.setEffectiveTo(newEffectiveTo);
        contract.setDurationMonths(newDuration);
        contract.setStatus(ContractStatus.ACTIVE); // reset từ EXPIRING_SOON nếu có

        // Ghi chú gia hạn
        String extendNote = "Gia hạn thêm " + request.additionalMonths() + " tháng → hết hạn " + newEffectiveTo;
        if (request.notes() != null && !request.notes().isBlank()) {
            extendNote += " (" + request.notes() + ")";
        }
        contract.setNotes((contract.getNotes() != null ? contract.getNotes() + "\n" : "") + extendNote);

        return enrich(contractRepository.save(contract), companyId);
    }

    /**
     * Tái ký HĐ mới — đánh dấu HĐ cũ hết hạn, tạo HĐ mới cho cùng nhân viên.
     */
    @Transactional
    public EmployeeContractResponse renew(String oldPublicId, RenewContractRequest request, Long companyId) {
        EmployeeContract oldContract = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(oldPublicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));

        if (oldContract.getStatus() != ContractStatus.ACTIVE
                && oldContract.getStatus() != ContractStatus.EXPIRING_SOON
                && oldContract.getStatus() != ContractStatus.EXPIRED) {
            throw new BusinessException("Không thể tái ký từ hợp đồng đã chấm dứt", HttpStatus.BAD_REQUEST);
        }

        // Validate contract type mới
        ContractType newType = contractTypeRepository.findByIdAndCompanyId(request.contractTypeId(), companyId)
                .orElseThrow(() -> new NotFoundException("ContractType", request.contractTypeId()));

        // Tính effectiveTo cho HĐ mới
        LocalDate effectiveTo = request.effectiveTo();
        Integer durationMonths = request.durationMonths();
        if (effectiveTo == null && durationMonths != null && durationMonths > 0) {
            effectiveTo = request.effectiveFrom().plusMonths(durationMonths);
        }
        if (!newType.isHasExpiry()) {
            effectiveTo = null;
            durationMonths = null;
        }

        // Đánh dấu HĐ cũ hết hạn
        oldContract.setStatus(ContractStatus.EXPIRED);
        oldContract.setNotes((oldContract.getNotes() != null ? oldContract.getNotes() + "\n" : "")
                + "Tái ký HĐ mới ngày " + LocalDate.now());
        contractRepository.save(oldContract);

        // Tạo HĐ mới
        EmployeeContract newContract = EmployeeContract.builder()
                .companyId(companyId)
                .employeeId(oldContract.getEmployeeId())
                .contractTypeId(request.contractTypeId())
                .contractNumber(request.contractNumber())
                .signedDate(request.signedDate())
                .effectiveFrom(request.effectiveFrom())
                .effectiveTo(effectiveTo)
                .durationMonths(durationMonths)
                .documentId(request.documentId())
                .notes(request.notes())
                .status(ContractStatus.ACTIVE)
                .build();

        return enrich(contractRepository.save(newContract), companyId);
    }

    @Transactional
    public void terminate(String publicId, Long companyId, String reason) {
        EmployeeContract contract = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRING_SOON) {
            throw new BusinessException("Only ACTIVE or EXPIRING_SOON contracts can be terminated", HttpStatus.BAD_REQUEST);
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setNotes((contract.getNotes() != null ? contract.getNotes() + "\n" : "") + "Terminated: " + reason);
        contractRepository.save(contract);
    }

    @Transactional
    public void delete(String publicId, Long companyId) {
        EmployeeContract contract = contractRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Contract not found"));
        contract.softDelete();
        contractRepository.save(contract);
    }

    /**
     * Scheduled method: cập nhật trạng thái HĐ hết hạn.
     * Gọi từ scheduled job hoặc admin API.
     */
    @Transactional
    public int refreshStatuses(Long companyId) {
        LocalDate today = LocalDate.now();
        int updated = 0;

        // Mark overdue contracts as EXPIRED
        List<EmployeeContract> overdue = contractRepository.findOverdue(companyId, today);
        for (EmployeeContract c : overdue) {
            c.setStatus(ContractStatus.EXPIRED);
            updated++;
        }
        if (!overdue.isEmpty()) contractRepository.saveAll(overdue);

        // Mark contracts expiring within 30 days as EXPIRING_SOON
        List<EmployeeContract> expiringSoon = contractRepository.findExpiringSoon(companyId, today, today.plusDays(30));
        for (EmployeeContract c : expiringSoon) {
            if (c.getStatus() == ContractStatus.ACTIVE) {
                c.setStatus(ContractStatus.EXPIRING_SOON);
                updated++;
            }
        }
        if (!expiringSoon.isEmpty()) contractRepository.saveAll(expiringSoon);

        return updated;
    }

    // ── Group by Employee ─────────────────────────────────

    /**
     * Gom nhóm hợp đồng theo nhân viên.
     * Mỗi phần tử = 1 NV + tổng hợp HĐ + danh sách HĐ nested.
     */
    @Transactional(readOnly = true)
    public List<EmployeeContractGroupDto> getGroupedByEmployee(Long companyId) {
        // 1. Query tất cả contracts
        List<EmployeeContract> allContracts = contractRepository
                .findByCompanyIdAndDeletedAtIsNull(companyId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        // 2. Group by employeeId
        Map<Long, List<EmployeeContract>> grouped = new LinkedHashMap<>();
        for (EmployeeContract c : allContracts) {
            grouped.computeIfAbsent(c.getEmployeeId(), k -> new ArrayList<>()).add(c);
        }

        // 3. Build DTO for each employee
        LocalDate today = LocalDate.now();
        List<EmployeeContractGroupDto> result = new ArrayList<>();

        for (Map.Entry<Long, List<EmployeeContract>> entry : grouped.entrySet()) {
            Long empId = entry.getKey();
            List<EmployeeContract> contracts = entry.getValue();

            // Sort by effectiveFrom DESC (newest first)
            contracts.sort((a, b) -> b.getEffectiveFrom().compareTo(a.getEffectiveFrom()));

            // Employee info
            Employee emp = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(empId, companyId).orElse(null);
            String empName = emp != null ? emp.getFullName() : "N/A";
            String empCode = emp != null ? emp.getEmployeeCode() : "N/A";

            // Contract type names (unique)
            List<String> typeNames = new ArrayList<>();
            Set<String> typeNameSet = new LinkedHashSet<>();
            for (EmployeeContract c : contracts) {
                ContractType ct = contractTypeRepository.findByIdAndCompanyId(c.getContractTypeId(), companyId).orElse(null);
                if (ct != null) typeNameSet.add(ct.getName());
            }
            typeNames.addAll(typeNameSet);

            // Current contract = CONTRACT category + ACTIVE/EXPIRING_SOON + newest effectiveFrom
            EmployeeContract currentContract = null;
            for (EmployeeContract c : contracts) {
                ContractType ct = contractTypeRepository.findByIdAndCompanyId(c.getContractTypeId(), companyId).orElse(null);
                if (ct != null && ct.getCategory() == com.hrms.domain.common.enums.ContractCategory.CONTRACT
                        && (c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.EXPIRING_SOON)) {
                    currentContract = c;
                    break; // sorted DESC, first match = newest
                }
            }
            // Fallback: newest CONTRACT (any status) if no ACTIVE
            if (currentContract == null) {
                for (EmployeeContract c : contracts) {
                    ContractType ct = contractTypeRepository.findByIdAndCompanyId(c.getContractTypeId(), companyId).orElse(null);
                    if (ct != null && ct.getCategory() == com.hrms.domain.common.enums.ContractCategory.CONTRACT) {
                        currentContract = c;
                        break;
                    }
                }
            }

            String currentTypeName = null;
            ContractStatus currentStatus = null;
            LocalDate currentEffectiveTo = null;
            if (currentContract != null) {
                ContractType ct = contractTypeRepository.findByIdAndCompanyId(currentContract.getContractTypeId(), companyId).orElse(null);
                currentTypeName = ct != null ? ct.getName() : null;
                currentStatus = currentContract.getStatus();
                currentEffectiveTo = currentContract.getEffectiveTo();
            }

            // Worst status (CONTRACT category only): EXPIRED > EXPIRING_SOON > ACTIVE > TERMINATED
            ContractStatus worstStatus = ContractStatus.ACTIVE;
            for (EmployeeContract c : contracts) {
                ContractType ct = contractTypeRepository.findByIdAndCompanyId(c.getContractTypeId(), companyId).orElse(null);
                if (ct != null && ct.getCategory() == com.hrms.domain.common.enums.ContractCategory.CONTRACT) {
                    if (c.getStatus() == ContractStatus.EXPIRED) {
                        worstStatus = ContractStatus.EXPIRED;
                        break; // can't get worse
                    } else if (c.getStatus() == ContractStatus.EXPIRING_SOON && worstStatus != ContractStatus.EXPIRED) {
                        worstStatus = ContractStatus.EXPIRING_SOON;
                    }
                }
            }
            if (currentStatus != null && worstStatus == ContractStatus.ACTIVE) {
                worstStatus = currentStatus;
            }

            // Days until expiry
            Long daysUntilExpiry = null;
            if (currentEffectiveTo != null) {
                daysUntilExpiry = ChronoUnit.DAYS.between(today, currentEffectiveTo);
            }

            // Active count
            int activeCount = (int) contracts.stream()
                    .filter(c -> c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.EXPIRING_SOON)
                    .count();

            // Enrich contracts to response
            List<EmployeeContractResponse> contractResponses = contracts.stream()
                    .map(c -> enrich(c, companyId))
                    .toList();

            result.add(new EmployeeContractGroupDto(
                    empId, empName, empCode,
                    contracts.size(), activeCount,
                    typeNames, currentTypeName, currentStatus, currentEffectiveTo,
                    worstStatus, daysUntilExpiry, contractResponses));
        }

        // Sort: worst status priority (EXPIRED first, then EXPIRING_SOON, then others)
        result.sort((a, b) -> {
            int pa = statusPriority(a.worstStatus());
            int pb = statusPriority(b.worstStatus());
            if (pa != pb) return pb - pa; // higher priority first
            return a.employeeName().compareToIgnoreCase(b.employeeName());
        });

        return result;
    }

    private int statusPriority(ContractStatus s) {
        if (s == null) return 0;
        return switch (s) {
            case EXPIRED -> 3;
            case EXPIRING_SOON -> 2;
            case ACTIVE -> 1;
            case TERMINATED -> 0;
        };
    }

    // ── Enrichment ───────────────────────────────────────

    private EmployeeContractResponse enrich(EmployeeContract c, Long companyId) {
        String employeeName = null;
        String employeeCode = null;
        String employeePublicId = null;
        Employee emp = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(c.getEmployeeId(), companyId).orElse(null);
        if (emp != null) {
            employeeName = emp.getFullName();
            employeeCode = emp.getEmployeeCode();
            employeePublicId = emp.getPublicId();
        }

        String contractTypeName = null;
        String contractTypeCategory = null;
        ContractType ct = contractTypeRepository.findByIdAndCompanyId(c.getContractTypeId(), companyId).orElse(null);
        if (ct != null) {
            contractTypeName = ct.getName();
            contractTypeCategory = ct.getCategory().name();
        }

        return new EmployeeContractResponse(
                c.getId(), c.getPublicId(),
                c.getEmployeeId(), employeePublicId, employeeName, employeeCode,
                c.getContractTypeId(), contractTypeName, contractTypeCategory,
                c.getContractNumber(), c.getSignedDate(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.getDurationMonths(),
                c.getStatus(), c.getDocumentId(), c.getNotes(), c.getCreatedAt());
    }
}
