package com.hrms.domain.asset.service;

import com.hrms.domain.asset.dto.AssetAssignmentResponse;
import com.hrms.domain.asset.dto.AssetResponse;
import com.hrms.domain.asset.dto.AssignAssetRequest;
import com.hrms.domain.asset.dto.CreateAssetRequest;
import com.hrms.domain.asset.dto.ReturnAssetRequest;
import com.hrms.domain.asset.entity.Asset;
import com.hrms.domain.asset.entity.AssetAssignment;
import com.hrms.domain.asset.entity.AssetCategory;
import com.hrms.domain.asset.repository.AssetAssignmentRepository;
import com.hrms.domain.asset.repository.AssetCategoryRepository;
import com.hrms.domain.asset.repository.AssetRepository;
import com.hrms.domain.common.enums.AssetCondition;
import com.hrms.domain.common.enums.AssetOwnershipType;
import com.hrms.domain.common.enums.AssetStatus;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.vendor.entity.Vendor;
import com.hrms.domain.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final VendorRepository vendorRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * @param usedBy Filter by who is using: "INTERNAL" | "VENDOR:{vendorId}" (e.g. "VENDOR:5")
     */
    @Transactional(readOnly = true)
    public Page<AssetResponse> listByCompany(Long companyId, Pageable pageable,
                                             String status, Long categoryId, String ownershipType, String usedBy) {
        Page<Asset> page;

        // usedBy filter: "INTERNAL" or "VENDOR:5"
        if (usedBy != null && !usedBy.isEmpty()) {
            String sourceType;
            Long vendorId = null;
            if (usedBy.startsWith("VENDOR:")) {
                sourceType = "VENDOR";
                vendorId = Long.parseLong(usedBy.substring(7));
            } else {
                sourceType = "INTERNAL";
            }
            page = assetRepository.findByUsedByFilter(companyId, sourceType, vendorId, pageable);
        } else {
            boolean hasFilter = status != null || categoryId != null || ownershipType != null;

            if (hasFilter) {
                AssetStatus assetStatus = status != null ? AssetStatus.valueOf(status) : null;
                AssetOwnershipType ownership = ownershipType != null ? AssetOwnershipType.valueOf(ownershipType) : null;
                page = assetRepository.findByCompanyIdWithFilters(companyId, assetStatus, categoryId, ownership, pageable);
            } else {
                page = assetRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);
            }
        }

        return page.map(asset -> enrichAssetResponse(asset, companyId));
    }

    @Transactional(readOnly = true)
    public AssetResponse getById(Long id, Long companyId) {
        Asset asset = assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", id));
        return enrichAssetResponse(asset, companyId);
    }

    @Transactional
    public AssetResponse create(CreateAssetRequest request, Long companyId) {
        assetCategoryRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.categoryId(), companyId)
                .orElseThrow(() -> new NotFoundException("AssetCategory", request.categoryId()));

        if (request.ownerVendorId() != null) {
            vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.ownerVendorId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Owner Vendor", request.ownerVendorId()));
        }

        if (request.supplierVendorId() != null) {
            vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.supplierVendorId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Supplier Vendor", request.supplierVendorId()));
        }

        String assetCode = generateAssetCode(companyId);

        AssetCondition condition = AssetCondition.valueOf(request.assetCondition() != null ? request.assetCondition() : "NEW");
        AssetOwnershipType ownership = AssetOwnershipType.valueOf(request.ownershipType());

        Asset asset = Asset.builder()
                .companyId(companyId)
                .assetCode(assetCode)
                .name(request.name())
                .serialNumber(request.serialNumber())
                .labelCode(request.labelCode())
                .description(request.description())
                .categoryId(request.categoryId())
                .ownershipType(ownership)
                .ownerVendorId(request.ownerVendorId())
                .supplierVendorId(request.supplierVendorId())
                .rented(request.isRented())
                .rentalNotes(request.rentalNotes())
                .purchasePrice(request.purchasePrice())
                .purchaseDate(request.purchaseDate())
                .warrantyMonths(request.warrantyMonths())
                .status(AssetStatus.AVAILABLE)
                .assetCondition(condition)
                .notes(request.notes())
                .build();

        asset = assetRepository.save(asset);
        log.info("Asset created: {} ({}) for company: {}", asset.getId(), assetCode, companyId);

        return enrichAssetResponse(asset, companyId);
    }

    @Transactional
    public AssetResponse update(Long id, CreateAssetRequest request, Long companyId) {
        Asset asset = assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", id));

        assetCategoryRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.categoryId(), companyId)
                .orElseThrow(() -> new NotFoundException("AssetCategory", request.categoryId()));

        if (request.ownerVendorId() != null) {
            vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.ownerVendorId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Owner Vendor", request.ownerVendorId()));
        }

        if (request.supplierVendorId() != null) {
            vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.supplierVendorId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Supplier Vendor", request.supplierVendorId()));
        }

        asset.setName(request.name());
        asset.setSerialNumber(request.serialNumber());
        asset.setLabelCode(request.labelCode());
        asset.setDescription(request.description());
        asset.setCategoryId(request.categoryId());
        asset.setOwnershipType(AssetOwnershipType.valueOf(request.ownershipType()));
        asset.setOwnerVendorId(request.ownerVendorId());
        asset.setSupplierVendorId(request.supplierVendorId());
        asset.setRented(request.isRented());
        asset.setRentalNotes(request.rentalNotes());
        asset.setPurchasePrice(request.purchasePrice());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setWarrantyMonths(request.warrantyMonths());
        asset.setAssetCondition(AssetCondition.valueOf(request.assetCondition() != null ? request.assetCondition() : "NEW"));
        asset.setNotes(request.notes());

        asset = assetRepository.save(asset);
        log.info("Asset updated: {} for company: {}", id, companyId);

        return enrichAssetResponse(asset, companyId);
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        Asset asset = assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", id));
        asset.softDelete();
        assetRepository.save(asset);
        log.info("Asset deleted (soft): {} for company: {}", id, companyId);
    }

    @Transactional
    public AssetResponse assignAsset(Long assetId, AssignAssetRequest request, Long companyId, Long assignedByUserId) {
        Asset asset = assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(assetId, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", assetId));

        if (asset.getStatus() != AssetStatus.AVAILABLE) {
            throw new BusinessException("Asset is not available for assignment. Current status: " + asset.getStatus());
        }

        employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.employeeId(), companyId)
                .orElseThrow(() -> new NotFoundException("Employee", request.employeeId()));

        AssetCondition condition = AssetCondition.valueOf(request.conditionOnAssign());

        AssetAssignment assignment = AssetAssignment.builder()
                .companyId(companyId)
                .assetId(assetId)
                .employeeId(request.employeeId())
                .assignedDate(request.assignedDate())
                .assignedBy(assignedByUserId)
                .conditionOnAssign(condition)
                .monthlyRentalPrice(request.monthlyRentalPrice())
                .notes(request.notes())
                .build();

        assetAssignmentRepository.save(assignment);

        asset.setStatus(AssetStatus.IN_USE);
        asset = assetRepository.save(asset);

        log.info("Asset {} assigned to employee {} for company: {}", assetId, request.employeeId(), companyId);

        return enrichAssetResponse(asset, companyId);
    }

    @Transactional
    public AssetResponse returnAsset(Long assetId, ReturnAssetRequest request, Long companyId) {
        Asset asset = assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(assetId, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", assetId));

        AssetAssignment activeAssignment = assetAssignmentRepository.findByAssetIdAndReturnedDateIsNull(assetId)
                .orElseThrow(() -> new BusinessException("Asset has no active assignment to return"));

        if (!activeAssignment.getCompanyId().equals(companyId)) {
            throw new BusinessException("Assignment does not belong to this company");
        }

        AssetCondition returnCondition = AssetCondition.valueOf(request.conditionOnReturn());

        activeAssignment.setReturnedDate(request.returnedDate());
        activeAssignment.setConditionOnReturn(returnCondition);
        if (request.notes() != null) {
            activeAssignment.setNotes(request.notes());
        }
        assetAssignmentRepository.save(activeAssignment);

        asset.setStatus(AssetStatus.AVAILABLE);
        asset.setAssetCondition(returnCondition);
        asset = assetRepository.save(asset);

        log.info("Asset {} returned from employee for company: {}", assetId, companyId);

        return enrichAssetResponse(asset, companyId);
    }

    @Transactional(readOnly = true)
    public List<AssetAssignmentResponse> getAssignmentHistory(Long assetId, Long companyId) {
        assetRepository.findByIdAndCompanyIdAndDeletedAtIsNull(assetId, companyId)
                .orElseThrow(() -> new NotFoundException("Asset", assetId));

        List<AssetAssignment> assignments = assetAssignmentRepository.findAssignmentHistoryByAsset(assetId, companyId);
        return assignments.stream().map(a -> {
            Asset asset = assetRepository.findById(a.getAssetId()).orElse(null);
            String assetCode = asset != null ? asset.getAssetCode() : "";
            String assetName = asset != null ? asset.getName() : "";
            String serialNumber = asset != null ? asset.getSerialNumber() : null;
            Employee emp = employeeRepository.findById(a.getEmployeeId()).orElse(null);
            String empName = emp != null ? emp.getFullName() : "";
            String empCode = emp != null ? emp.getEmployeeCode() : "";
            return new AssetAssignmentResponse(
                    a.getId(), a.getAssetId(), assetCode, assetName, serialNumber,
                    a.getEmployeeId(), empName, empCode,
                    a.getAssignedDate(), a.getReturnedDate(),
                    a.getAssignedBy(), "",
                    a.getConditionOnAssign() != null ? a.getConditionOnAssign().name() : "",
                    a.getConditionOnReturn() != null ? a.getConditionOnReturn().name() : null,
                    a.getMonthlyRentalPrice(),
                    a.getNotes()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> getEmployeeAssets(Long employeeId, Long companyId) {
        employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(employeeId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", employeeId));

        List<AssetAssignment> activeAssignments = assetAssignmentRepository
                .findByEmployeeIdAndReturnedDateIsNullAndCompanyId(employeeId, companyId);

        return activeAssignments.stream()
                .map(assignment -> assetRepository.findById(assignment.getAssetId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(asset -> enrichAssetResponse(asset, companyId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetAssignmentResponse> getEmployeeAssignmentHistory(Long employeeId, Long companyId) {
        List<AssetAssignment> assignments = assetAssignmentRepository
                .findByEmployeeIdAndCompanyIdOrderByAssignedDateDesc(employeeId, companyId);
        return assignments.stream().map(a -> {
            Asset asset = assetRepository.findById(a.getAssetId()).orElse(null);
            String assetCode = asset != null ? asset.getAssetCode() : "";
            String assetName = asset != null ? asset.getName() : "";
            String serialNumber = asset != null ? asset.getSerialNumber() : null;
            Employee emp = employeeRepository.findById(a.getEmployeeId()).orElse(null);
            String empName = emp != null ? emp.getFullName() : "";
            String empCode = emp != null ? emp.getEmployeeCode() : "";
            return new AssetAssignmentResponse(
                    a.getId(), a.getAssetId(), assetCode, assetName, serialNumber,
                    a.getEmployeeId(), empName, empCode,
                    a.getAssignedDate(), a.getReturnedDate(),
                    a.getAssignedBy(), "",
                    a.getConditionOnAssign() != null ? a.getConditionOnAssign().name() : "",
                    a.getConditionOnReturn() != null ? a.getConditionOnReturn().name() : null,
                    a.getMonthlyRentalPrice(),
                    a.getNotes()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public AssetStatsDto getStats(Long companyId) {
        long total = assetRepository.countTotalByCompanyId(companyId);
        long inUse = assetRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, AssetStatus.IN_USE);
        long available = assetRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, AssetStatus.AVAILABLE);
        long rented = assetRepository.countAssignedToVendorEmployees(companyId);

        return new AssetStatsDto(total, inUse, available, rented);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> getWarrantyAlerts(Long companyId) {
        return assetRepository.findWarrantyAlertsForCompany(companyId)
                .stream()
                .map(asset -> enrichAssetResponse(asset, companyId))
                .toList();
    }

    private AssetResponse enrichAssetResponse(Asset asset, Long companyId) {
        String categoryName = "";
        AssetCategory category = assetCategoryRepository.findById(asset.getCategoryId()).orElse(null);
        if (category != null) {
            categoryName = category.getName();
        }

        String ownerVendorName = "";
        if (asset.getOwnerVendorId() != null) {
            Vendor ownerVendor = vendorRepository.findById(asset.getOwnerVendorId()).orElse(null);
            if (ownerVendor != null) {
                ownerVendorName = ownerVendor.getName();
            }
        }

        String supplierVendorName = "";
        if (asset.getSupplierVendorId() != null) {
            Vendor supplierVendor = vendorRepository.findById(asset.getSupplierVendorId()).orElse(null);
            if (supplierVendor != null) {
                supplierVendorName = supplierVendor.getName();
            }
        }

        String currentHolderName = "";
        Long currentHolderId = null;
        Optional<AssetAssignment> activeAssignment = assetAssignmentRepository.findByAssetIdAndReturnedDateIsNull(asset.getId());
        if (activeAssignment.isPresent()) {
            Employee employee = employeeRepository.findById(activeAssignment.get().getEmployeeId()).orElse(null);
            if (employee != null) {
                currentHolderName = employee.getFullName();
                currentHolderId = employee.getId();
            }
        }

        return new AssetResponse(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getSerialNumber(),
                asset.getLabelCode(),
                asset.getDescription(),
                asset.getCategoryId(),
                categoryName,
                asset.getOwnershipType().name(),
                asset.getOwnerVendorId(),
                ownerVendorName,
                asset.getSupplierVendorId(),
                supplierVendorName,
                asset.isRented(),
                asset.getRentalNotes(),
                asset.getPurchasePrice(),
                asset.getPurchaseDate(),
                asset.getWarrantyMonths(),
                asset.getStatus().name(),
                asset.getAssetCondition().name(),
                asset.getNotes(),
                currentHolderName,
                currentHolderId,
                asset.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public String getNextCode(Long companyId) {
        return generateAssetCode(companyId);
    }

    private String generateAssetCode(Long companyId) {
        long base = assetRepository.countByCompanyId(companyId) + 1;
        String code;
        do {
            code = String.format("TS%04d", base++);
        } while (assetRepository.existsByAssetCodeAndCompanyIdAndDeletedAtIsNull(code, companyId));
        return code;
    }

    public record AssetStatsDto(long total, long inUse, long available, long rented) {}
}
     