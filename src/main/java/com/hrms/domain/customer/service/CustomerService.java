package com.hrms.domain.customer.service;

import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.customer.dto.*;
import com.hrms.domain.customer.entity.Customer;
import com.hrms.domain.customer.entity.CustomerPrice;
import com.hrms.domain.customer.mapper.CustomerMapper;
import com.hrms.domain.customer.repository.CustomerPriceRepository;
import com.hrms.domain.customer.repository.CustomerRepository;
import com.hrms.domain.jobrole.repository.JobRoleRepository;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final JobRoleRepository jobRoleRepository;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listByCompany(Long companyId, Pageable pageable) {
        return customerRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
                .map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listActive(Long companyId) {
        return customerRepository.findByCompanyIdAndActiveAndDeletedAtIsNull(companyId, true)
                .stream().map(customerMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id, Long companyId) {
        return customerMapper.toResponse(
                customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                        .orElseThrow(() -> new NotFoundException("Customer", id)));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request, Long companyId) {
        if (customerRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.code(), companyId)) {
            throw new DuplicateResourceException("Customer code already exists: " + request.code());
        }
        Customer customer = Customer.builder()
                .companyId(companyId)
                .code(request.code())
                .vendorCode(request.vendorCode())
                .name(request.name())
                .contactPerson(request.contactPerson())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .address(request.address())
                .taxCode(request.taxCode())
                .notes(request.notes())
                .active(true)
                .build();
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CreateCustomerRequest request, Long companyId) {
        Customer customer = customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        customer.setName(request.name());
        customer.setVendorCode(request.vendorCode());
        customer.setContactPerson(request.contactPerson());
        customer.setContactEmail(request.contactEmail());
        customer.setContactPhone(request.contactPhone());
        customer.setAddress(request.address());
        customer.setTaxCode(request.taxCode());
        customer.setNotes(request.notes());
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        Customer customer = customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        customer.softDelete();
        customerRepository.save(customer);
    }

    // ── Price management ──

    @Transactional
    public void setPrice(Long customerId, Long companyId, SetCustomerPriceRequest request) {
        customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, companyId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        jobRoleRepository.findByCompanyIdAndIdAndDeletedAtIsNull(companyId, request.jobRoleId())
                .orElseThrow(() -> new NotFoundException("JobRole", request.jobRoleId()));

        customerPriceRepository.closeCurrentPrice(customerId, request.jobRoleId(),
                request.effectiveFrom().minusDays(1));

        CustomerPrice newPrice = CustomerPrice.builder()
                .companyId(companyId)
                .customerId(customerId)
                .jobRoleId(request.jobRoleId())
                .price(request.price())
                .priceUnit(request.priceUnit() != null ? request.priceUnit() : com.hrms.domain.common.enums.PriceUnit.MONTHLY)
                .currency(request.currency() != null ? request.currency() : "VND")
                .effectiveFrom(request.effectiveFrom())
                .notes(request.notes())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        customerPriceRepository.save(newPrice);
    }

    @Transactional(readOnly = true)
    public List<CustomerPriceResponse> getPriceHistory(Long customerId, Long companyId) {
        customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(customerId, companyId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        return customerPriceRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId)
                .stream()
                .map(p -> {
                    String jobRoleName = jobRoleRepository.findById(p.getJobRoleId())
                            .map(jr -> jr.getName()).orElse("Unknown");
                    return customerMapper.toPriceResponse(p, jobRoleName);
                })
                .toList();
    }
}
