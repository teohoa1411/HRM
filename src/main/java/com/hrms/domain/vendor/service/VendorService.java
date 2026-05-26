package com.hrms.domain.vendor.service;

import com.hrms.domain.account.entity.User;
import com.hrms.domain.account.repository.UserRepository;
import com.hrms.domain.common.enums.Role;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.vendor.dto.CreateVendorRequest;
import com.hrms.domain.vendor.dto.VendorResponse;
import com.hrms.domain.vendor.entity.Vendor;
import com.hrms.domain.vendor.mapper.VendorMapper;
import com.hrms.domain.vendor.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class VendorService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final VendorMapper vendorMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<VendorResponse> listByCompany(Long companyId, Pageable pageable) {
        return vendorRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
                .map(vendorMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public VendorResponse getById(Long id, Long companyId) {
        Vendor vendor = vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor", id));
        return vendorMapper.toResponse(vendor);
    }

    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request, Long companyId) {
        if (vendorRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.code(), companyId)) {
            throw new DuplicateResourceException("Vendor code already exists in this company: " + request.code());
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.accountUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.accountUsername());
        }

        Vendor vendor = Vendor.builder()
                .companyId(companyId)
                .code(request.code())
                .name(request.name())
                .vendorType(request.vendorType())
                .contactPerson(request.contactPerson())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .address(request.address())
                .taxCode(request.taxCode())
                .notes(request.notes())
                .active(true)
                .build();

        vendor = vendorRepository.save(vendor);

        // Create VENDOR_USER account
        String tempPassword = generateTemporaryPassword();
        User vendorUser = User.builder()
                .username(request.accountUsername())
                .email(request.accountEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(Role.VENDOR_USER)
                .companyId(companyId)
                .referenceId(vendor.getId())
                .active(true)
                .mustChangePassword(true)
                .build();

        vendorUser = userRepository.save(vendorUser);
        vendor.setAccountUserId(vendorUser.getId());
        vendorRepository.save(vendor);

        return vendorMapper.toResponse(vendor);
    }

    @Transactional
    public VendorResponse updateVendor(Long id, CreateVendorRequest request, Long companyId) {
        Vendor vendor = vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor", id));

        vendor.setName(request.name());
        vendor.setVendorType(request.vendorType());
        vendor.setContactPerson(request.contactPerson());
        vendor.setContactEmail(request.contactEmail());
        vendor.setContactPhone(request.contactPhone());
        vendor.setAddress(request.address());
        vendor.setNotes(request.notes());

        return vendorMapper.toResponse(vendorRepository.save(vendor));
    }

    @Transactional
    public void deleteVendor(Long id, Long companyId) {
        Vendor vendor = vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor", id));
        vendor.softDelete();
        vendorRepository.save(vendor);
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
