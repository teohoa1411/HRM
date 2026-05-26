package com.hrms.domain.employee.service;

import com.hrms.domain.account.entity.User;
import com.hrms.domain.account.repository.UserRepository;
import com.hrms.domain.common.enums.EmployeeSourceType;
import com.hrms.domain.common.enums.EmployeeStatus;
import com.hrms.domain.common.enums.Role;
import com.hrms.domain.common.event.EmployeeTerminatedEvent;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.employee.dto.CreateEmployeeRequest;
import com.hrms.domain.employee.dto.EmployeeResponse;
import com.hrms.domain.employee.entity.Employee;
import com.hrms.domain.employee.entity.EmployeeDetail;
import com.hrms.domain.employee.mapper.EmployeeMapper;
import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.jobrole.service.JobRoleService;
import com.hrms.domain.salary.entity.InternalSalary;
import com.hrms.domain.salary.repository.InternalSalaryRepository;
import com.hrms.domain.vendor.entity.Vendor;
import com.hrms.domain.vendor.repository.VendorRepository;
import com.hrms.domain.company.entity.Department;
import com.hrms.domain.company.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String DEFAULT_PASSWORD = "Admin@123";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final InternalSalaryRepository salaryRepository;
    private final VendorRepository vendorRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final JobRoleService jobRoleService;
    private final com.hrms.domain.leave.service.LeaveService leaveService;

    // ── Queries ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> listByCompany(Long companyId, Pageable pageable) {
        Page<Employee> page = employeeRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

        // Batch-load usernames to avoid N+1
        List<Long> userIds = page.getContent().stream()
                .map(Employee::getUserId).filter(id -> id != null).toList();
        Map<Long, String> usernameByUserId = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // Batch-load vendor names to avoid N+1
        List<Long> vendorIds = page.getContent().stream()
                .map(Employee::getVendorId).filter(id -> id != null).toList();
        Map<Long, String> vendorNameById = vendorRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName));

        return page.map(emp -> {
            EmployeeResponse base = employeeMapper.toResponse(emp);
            String username   = emp.getUserId()   != null ? usernameByUserId.get(emp.getUserId())   : null;
            String vendorName = emp.getVendorId()  != null ? vendorNameById.get(emp.getVendorId())   : null;
            return withEnrichment(base, username, vendorName, null, null);
        });
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id, Long companyId) {
        Employee emp = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", id));
        return enrichFull(emp);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getByPublicId(String publicId, Long companyId) {
        Employee emp = employeeRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        return enrichFull(emp);
    }

    /**
     * Resolve publicId → internal Long id.
     * Used by controllers that need the internal ID for service calls.
     */
    @Transactional(readOnly = true)
    public Long resolveEmployeeId(String publicId, Long companyId) {
        return employeeRepository.findByPublicIdAndCompanyIdAndDeletedAtIsNull(publicId, companyId)
                .map(Employee::getId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getByUserId(Long userId) {
        Employee emp = employeeRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile not found"));
        return enrichFull(emp);
    }

    // ── Create ───────────────────────────────────────────────

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request, Long companyId) {
        if (request.sourceType() == EmployeeSourceType.VENDOR && request.vendorId() == null) {
            throw new BusinessException("Vendor ID is required for VENDOR type employees", HttpStatus.BAD_REQUEST);
        }
        if (request.jobRoleId() != null) {
            jobRoleService.assertBelongsToCompany(request.jobRoleId(), companyId);
        }

        // ── Validate BEFORE any DB writes ───────────────────────
        // Email uniqueness: users.email has a global UNIQUE constraint
        if (request.emailWork() != null && !request.emailWork().isBlank()
                && userRepository.existsByEmailAndDeletedAtIsNull(request.emailWork())) {
            throw new BusinessException(
                    "Email '" + request.emailWork() + "' đã được sử dụng bởi tài khoản khác",
                    HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        }

        // Auto-generate employee code (NV0001, NV0002 …)
        String employeeCode = generateEmployeeCode(companyId);

        // Derive firstName / lastName from fullName
        String firstName = extractFirstName(request.fullName());
        String lastName  = extractLastName(request.fullName());

        Employee employee = Employee.builder()
                .companyId(companyId)
                .employeeCode(employeeCode)
                .fullName(request.fullName())
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(request.dateOfBirth())
                .emailWork(request.emailWork())
                .phoneWork(request.phoneWork())
                .emergencyPhone(request.emergencyPhone())
                .sourceType(request.sourceType())
                .vendorId(request.vendorId())
                .jobRoleId(request.jobRoleId())
                .departmentId(request.departmentId())
                .positionId(request.positionId())
                .directManagerId(request.directManagerId())
                .startDate(request.startDate())
                .status(request.status() != null ? request.status() : EmployeeStatus.ACTIVE)
                .employmentType(request.employmentType() != null ? request.employmentType() : "FULL_TIME")
                .probationEndDate(request.probationEndDate())
                .notes(request.notes())
                .build();

        employee = employeeRepository.save(employee);

        // Create EmployeeDetail record
        EmployeeDetail detail = EmployeeDetail.builder().employee(employee).build();
        employee.setDetail(detail);

        // Auto-generate username: {tên}{initials}{DDMM}, handle duplicates
        String username = generateUsername(request.fullName(), request.dateOfBirth());

        String userEmail = (request.emailWork() != null && !request.emailWork().isBlank())
                ? request.emailWork()
                : username + "@company.com";

        // Create EMPLOYEE user account (default password Admin@123, must change on first login)
        User employeeUser = User.builder()
                .username(username)
                .email(userEmail)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(request.accountRole() != null && !request.accountRole().isBlank()
                    ? Role.valueOf(request.accountRole()) : Role.EMPLOYEE)
                .companyId(companyId)
                .referenceId(employee.getId())
                .active(true)
                .mustChangePassword(true)
                .build();

        try {
            employeeUser = userRepository.save(employeeUser);
        } catch (DataIntegrityViolationException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("uq_users_email")) {
                throw new BusinessException(
                        "Email '" + userEmail + "' đã được sử dụng bởi tài khoản khác",
                        HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
            }
            if (cause != null && cause.contains("username")) {
                throw new BusinessException(
                        "Username đã tồn tại trong hệ thống",
                        HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS");
            }
            throw new BusinessException("Lỗi dữ liệu khi tạo tài khoản: " + cause, HttpStatus.CONFLICT, "DATA_ERROR");
        }
        employee.setUserId(employeeUser.getId());
        employee = employeeRepository.save(employee);

        // Auto-grant leave quota cho nhân sự INTERNAL (theo tỷ lệ tháng còn lại)
        if (employee.getSourceType() == EmployeeSourceType.INTERNAL) {
            try {
                leaveService.autoGrantQuotaForNewEmployee(companyId, employee.getId(), employee.getStartDate());
            } catch (Exception e) {
                // Không block tạo nhân sự nếu auto-grant lỗi
                log.warn("Failed to auto-grant leave quota for employee={}: {}", employee.getId(), e.getMessage());
            }
        }

        return enrichFull(employee);
    }

    // ── Update ───────────────────────────────────────────────

    @Transactional
    public EmployeeResponse updateEmployee(Long id, CreateEmployeeRequest request, Long companyId) {
        Employee employee = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", id));

        employee.setFullName(request.fullName());
        employee.setFirstName(extractFirstName(request.fullName()));
        employee.setLastName(extractLastName(request.fullName()));
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setEmailWork(request.emailWork());
        employee.setPhoneWork(request.phoneWork());
        employee.setEmergencyPhone(request.emergencyPhone());
        employee.setDepartmentId(request.departmentId());
        employee.setPositionId(request.positionId());
        employee.setDirectManagerId(request.directManagerId());
        employee.setEmploymentType(request.employmentType());
        employee.setNotes(request.notes());

        if (request.jobRoleId() != null && !request.jobRoleId().equals(employee.getJobRoleId())) {
            jobRoleService.assertBelongsToCompany(request.jobRoleId(), companyId);
            employee.setJobRoleId(request.jobRoleId());
        }

        return enrichFull(employeeRepository.save(employee));
    }

    @Transactional
    public void updateStatus(Long id, Long companyId, EmployeeStatus newStatus) {
        updateStatus(id, companyId, newStatus, null, null);
    }

    @Transactional
    public void updateStatus(Long id, Long companyId, EmployeeStatus newStatus,
                              LocalDate endDate, String reason) {
        Employee employee = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", id));
        employee.setStatus(newStatus);

        // Nếu nghỉ việc → set endDate và publish event
        if (newStatus == EmployeeStatus.TERMINATED) {
            LocalDate lastDay = endDate != null ? endDate : LocalDate.now();
            employee.setEndDate(lastDay);
            employeeRepository.save(employee);

            eventPublisher.publishEvent(new EmployeeTerminatedEvent(
                    this, companyId, id, lastDay,
                    reason != null ? reason : "Nghỉ việc"));
        } else {
            employeeRepository.save(employee);
        }
    }

    @Transactional
    public void deleteEmployee(Long id, Long companyId) {
        Employee employee = employeeRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new NotFoundException("Employee", id));
        employee.softDelete();
        employeeRepository.save(employee);
    }

    // ── Search employees by name or code ──────

    @Transactional(readOnly = true)
    public List<EmployeeResponse> searchEmployees(Long companyId, String term) {
        return employeeRepository.searchByNameOrCode(companyId, term)
                .stream()
                .map(employeeMapper::toResponse)
                .toList();
    }

    // ── Next code preview (for frontend disabled field) ──────

    @Transactional(readOnly = true)
    public String getNextCode(Long companyId) {
        return generateEmployeeCode(companyId);
    }

    // ── Preview username (for frontend disabled field) ───────

    public String previewUsername(String fullName, LocalDate dateOfBirth) {
        return generateUsername(fullName, dateOfBirth);
    }

    // ── Private helpers ──────────────────────────────────────

    /**
     * Enrich a mapped response with username + currentSalary.
     */
    private EmployeeResponse enrichFull(Employee emp) {
        EmployeeResponse base = employeeMapper.toResponse(emp);
        String username = null;
        if (emp.getUserId() != null) {
            username = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(emp.getUserId(), emp.getCompanyId())
                    .map(User::getUsername).orElse(null);
        }
        String vendorName = null;
        if (emp.getVendorId() != null) {
            vendorName = vendorRepository.findByIdAndCompanyIdAndDeletedAtIsNull(emp.getVendorId(), emp.getCompanyId())
                    .map(Vendor::getName).orElse(null);
        }
        String departmentName = null;
        if (emp.getDepartmentId() != null) {
            departmentName = departmentRepository.findByIdAndCompanyIdAndDeletedAtIsNull(emp.getDepartmentId(), emp.getCompanyId())
                    .map(Department::getName).orElse(null);
        }
        BigDecimal currentSalary = salaryRepository.findCurrentSalary(emp.getId())
                .map(InternalSalary::getMonthlySalary).orElse(null);
        return withEnrichment(base, username, vendorName, departmentName, currentSalary);
    }

    /**
     * Reconstruct the record with enriched fields (records are immutable).
     */
    private EmployeeResponse withEnrichment(EmployeeResponse base, String username, String vendorName,
                                             String departmentName, BigDecimal currentSalary) {
        return new EmployeeResponse(
                base.id(), base.publicId(), base.companyId(), base.employeeCode(), base.userId(),
                username,
                base.fullName(), base.firstName(), base.lastName(),
                base.dateOfBirth(),
                base.emailWork(), base.phoneWork(), base.emergencyPhone(),
                base.avatarS3Key(),
                base.sourceType(), base.vendorId(), vendorName,
                base.jobRoleId(), base.jobRoleName(),
                base.departmentId(), departmentName,
                base.positionId(), base.directManagerId(),
                base.startDate(), base.endDate(),
                base.status(), base.employmentType(),
                base.probationEndDate(),
                currentSalary,
                base.notes(),
                base.createdAt()
        );
    }

    /**
     * Auto-generate employee code: NV0001, NV0002 …
     * Skips codes already in use (handles gaps from soft-deletes).
     */
    private String generateEmployeeCode(Long companyId) {
        long base = employeeRepository.countByCompanyId(companyId) + 1;
        String code;
        do {
            code = String.format("NV%04d", base++);
        } while (employeeRepository.existsByEmployeeCodeAndCompanyIdAndDeletedAtIsNull(code, companyId));
        return code;
    }

    /**
     * Generate login username from fullName + dateOfBirth.
     *
     * Rule: {tên (last word, no diacritics)}{initials of họ+tên đệm}{DDMM}
     * Example: "Nguyễn Văn Hạnh", 14/11/1997 → "hanhnv1411"
     *
     * Appends numeric suffix (1, 2 …) if username already exists.
     */
    private String generateUsername(String fullName, LocalDate dateOfBirth) {
        String base = buildUsernameBase(fullName, dateOfBirth);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        int suffix = 1;
        String candidate;
        do {
            candidate = base + suffix++;
        } while (userRepository.existsByUsername(candidate));
        return candidate;
    }

    private String buildUsernameBase(String fullName, LocalDate dateOfBirth) {
        // Normalize: remove Vietnamese diacritics, lowercase
        String normalized = Normalizer.normalize(fullName, Normalizer.Form.NFD);
        normalized = Pattern.compile("[\\u0300-\\u036f]").matcher(normalized).replaceAll(""); // strip combining
        normalized = normalized.replace("đ", "d").replace("Đ", "d");
        normalized = Pattern.compile("[^a-zA-Z\\s]").matcher(normalized).replaceAll(""); // strip non-alpha
        normalized = normalized.toLowerCase().trim();

        String[] words = normalized.split("\\s+");
        if (words.length == 0 || normalized.isEmpty()) return "user";

        String firstName = words[words.length - 1]; // tên (last word)
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < words.length - 1; i++) {
            if (!words[i].isEmpty()) initials.append(words[i].charAt(0));
        }

        // DD + MM from dateOfBirth
        String dd = String.format("%02d", dateOfBirth.getDayOfMonth());
        String mm = String.format("%02d", dateOfBirth.getMonthValue());

        return firstName + initials + dd + mm;
    }

    /** Extract first name (tên) = last word in fullName. */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    /** Extract last name (họ + tên đệm) = all words except last. */
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String trimmed = fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        return lastSpace >= 0 ? trimmed.substring(0, lastSpace) : "";
    }
}