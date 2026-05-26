package com.hrms.domain.vendor.service;

import com.hrms.domain.common.enums.PriceUnit;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.domain.vendor.dto.SetVendorPriceRequest;
import com.hrms.domain.vendor.dto.VendorPriceResponse;
import com.hrms.domain.vendor.entity.VendorPrice;
import com.hrms.domain.vendor.repository.VendorPriceRepository;
import com.hrms.domain.vendor.repository.VendorRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorPriceService {

    private final VendorPriceRepository vendorPriceRepository;
    private final VendorRepository vendorRepository;
    private final JobRoleRepository jobRoleRepository;

    @Transactional
    public void setPrice(Long vendorId, Long companyId, SetVendorPriceRequest request) {
        vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(vendorId, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor", vendorId));
        jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, request.jobRoleId())
                .orElseThrow(() -> new NotFoundException("JobRole", request.jobRoleId()));

        vendorPriceRepository.closeCurrentPrice(vendorId, request.jobRoleId(),
                request.effectiveFrom().minusDays(1));

        VendorPrice newPrice = VendorPrice.builder()
                .companyId(companyId)
                .vendorId(vendorId)
                .jobRoleId(request.jobRoleId())
                .price(request.price())
                .priceUnit(request.priceUnit() != null ? request.priceUnit() : PriceUnit.MONTHLY)
                .currency(request.currency() != null ? request.currency() : "VND")
                .effectiveFrom(request.effectiveFrom())
                .notes(request.notes())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        vendorPriceRepository.save(newPrice);
    }

    @Transactional(readOnly = true)
    public List<VendorPriceResponse> getPriceHistory(Long vendorId, Long companyId) {
        vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(vendorId, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor", vendorId));
        return vendorPriceRepository.findByVendorIdOrderByEffectiveFromDesc(vendorId)
                .stream()
                .map(p -> {
                    String jobRoleName = jobRoleRepository.findById(p.getJobRoleId())
                            .map(jr -> jr.getName()).orElse("Unknown");
                    return new VendorPriceResponse(
                            p.getId(), p.getVendorId(), p.getJobRoleId(), jobRoleName,
                            p.getPrice(), p.getPriceUnit(), p.getCurrency(),
                            p.getEffectiveFrom(), p.getEffectiveTo(), p.getNotes(), p.getCreatedAt()
                    );
                })
                .toList();
    }
}
