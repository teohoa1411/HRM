package com.hrms.domain.company.service;

import com.hrms.domain.account.entity.User;
import com.hrms.domain.account.repository.UserRepository;
import com.hrms.domain.common.enums.Role;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.company.dto.CompanyResponse;
import com.hrms.domain.company.dto.CreateCompanyRequest;
import com.hrms.domain.company.entity.Company;
import com.hrms.domain.company.mapper.CompanyMapper;
import com.hrms.domain.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyMapper companyMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> listAll(Pageable pageable) {
        return companyRepository.findByDeletedAtIsNull(pageable).map(companyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(Long id) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Company", id));
        return companyMapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        if (companyRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new DuplicateResourceException("Company code already exists: " + request.code());
        }
        if (request.taxCode() != null && companyRepository.existsByTaxCodeAndDeletedAtIsNull(request.taxCode())) {
            throw new DuplicateResourceException("Tax code already exists: " + request.taxCode());
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.adminUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.adminUsername());
        }

        // Create company
        Company company = Company.builder()
                .code(request.code())
                .name(request.name())
                .shortName(request.shortName())
                .taxCode(request.taxCode())
                .businessLicense(request.businessLicense())
                .address(request.address())
                .city(request.city())
                .country(request.country() != null ? request.country() : "VN")
                .phone(request.phone())
                .email(request.email())
                .website(request.website())
                .active(true)
                .build();

        company = companyRepository.save(company);

        // Create COMPANY_ADMIN user
        String tempPassword = generateTemporaryPassword();
        User adminUser = User.builder()
                .username(request.adminUsername())
                .email(request.adminEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(Role.COMPANY_ADMIN)
                .companyId(company.getId())
                .active(true)
                .mustChangePassword(true)
                .build();

        adminUser = userRepository.save(adminUser);
        company.setAdminUserId(adminUser.getId());
        company = companyRepository.save(company);

        CompanyResponse base = companyMapper.toResponse(company);
        return new CompanyResponse(
                base.id(), base.code(), base.name(), base.shortName(), base.taxCode(),
                base.address(), base.city(), base.country(), base.phone(), base.email(),
                base.website(), base.adminUserId(),
                request.adminUsername(), request.adminEmail(),
                base.active(), base.createdAt(),
                tempPassword   // only returned once at creation
        );
    }

    @Transactional
    public CompanyResponse updateCompany(Long id, CreateCompanyRequest request) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Company", id));

        company.setName(request.name());
        company.setShortName(request.shortName());
        company.setTaxCode(request.taxCode());
        company.setAddress(request.address());
        company.setCity(request.city());
        company.setPhone(request.phone());
        company.setEmail(request.email());
        company.setWebsite(request.website());

        return companyMapper.toResponse(companyRepository.save(company));
    }

    @Transactional
    public void toggleCompanyStatus(Long id) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Company", id));
        company.setActive(!company.isActive());
        if (!company.isActive()) {
            userRepository.deactivateAllByCompanyId(id);
        }
        companyRepository.save(company);
    }

    @Transactional
    public void deleteCompany(Long id) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Company", id));
        company.softDelete();
        companyRepository.save(company);
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
