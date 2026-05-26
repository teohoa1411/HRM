package com.hrms.domain.account.service;

import com.hrms.domain.account.dto.CreateUserRequest;
import com.hrms.domain.account.dto.UserResponse;
import com.hrms.domain.account.entity.User;
import com.hrms.domain.account.mapper.UserMapper;
import com.hrms.domain.account.repository.UserRepository;
import com.hrms.domain.common.exception.DuplicateResourceException;
import com.hrms.domain.common.exception.NotFoundException;
import com.hrms.domain.common.exception.TenantAccessDeniedException;
import com.hrms.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> listByCompany(Long companyId, Pageable pageable) {
        return userRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
                .map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId, Long companyId) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long companyId) {
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new DuplicateResourceException("Username already exists: " + request.username());
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new DuplicateResourceException("Email already exists: " + request.email());
        }

        String tempPassword = generateTemporaryPassword();
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(request.role())
                .companyId(companyId)
                .active(true)
                .mustChangePassword(true)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void toggleUserStatus(Long userId, Long companyId) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        assertCanManageUser(user);
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    @Transactional
    public void unlockUser(Long userId, Long companyId) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        assertCanManageUser(user);
        user.resetFailedLoginCount();
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, Long companyId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new com.hrms.domain.common.exception.BusinessException(
                    "Current password is incorrect",
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    @Transactional
    public void resetPasswordToDefault(Long userId, Long companyId) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        user.setPasswordHash(passwordEncoder.encode("Admin@123"));
        user.setMustChangePassword(true);
        user.resetFailedLoginCount();
        userRepository.save(user);
    }

    @Transactional
    public void changeRole(Long userId, Long companyId, String newRoleStr) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (user.getRole() == com.hrms.domain.common.enums.Role.SUPER_ADMIN) {
            throw new com.hrms.domain.common.exception.BusinessException(
                    "Cannot change SUPER_ADMIN role", org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            throw new com.hrms.domain.common.exception.BusinessException(
                    "Cannot change your own role", org.springframework.http.HttpStatus.BAD_REQUEST, "SELF_ROLE_CHANGE");
        }

        com.hrms.domain.common.enums.Role newRole = com.hrms.domain.common.enums.Role.valueOf(newRoleStr);
        user.setRole(newRole);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId, Long companyId) {
        User user = userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        assertCanManageUser(user);
        user.softDelete();
        userRepository.save(user);
    }

    /**
     * Prevent COMPANY_ADMIN from modifying/deleting SUPER_ADMIN or other COMPANY_ADMIN accounts.
     * Only SUPER_ADMIN can manage SUPER_ADMIN and COMPANY_ADMIN users.
     */
    private void assertCanManageUser(User targetUser) {
        com.hrms.domain.common.enums.Role currentRole = SecurityUtils.getCurrentUser().role();
        com.hrms.domain.common.enums.Role targetRole = targetUser.getRole();

        // COMPANY_ADMIN không được quản lý SUPER_ADMIN hoặc COMPANY_ADMIN khác
        if (currentRole == com.hrms.domain.common.enums.Role.COMPANY_ADMIN
                && (targetRole == com.hrms.domain.common.enums.Role.SUPER_ADMIN
                    || targetRole == com.hrms.domain.common.enums.Role.COMPANY_ADMIN)) {
            throw new com.hrms.domain.common.exception.BusinessException(
                    "Không có quyền thao tác trên tài khoản admin",
                    org.springframework.http.HttpStatus.FORBIDDEN, "INSUFFICIENT_PRIVILEGE");
        }
        // MANAGER không được quản lý SUPER_ADMIN, COMPANY_ADMIN hoặc MANAGER khác
        if (currentRole == com.hrms.domain.common.enums.Role.MANAGER
                && (targetRole == com.hrms.domain.common.enums.Role.SUPER_ADMIN
                    || targetRole == com.hrms.domain.common.enums.Role.COMPANY_ADMIN
                    || targetRole == com.hrms.domain.common.enums.Role.MANAGER)) {
            throw new com.hrms.domain.common.exception.BusinessException(
                    "MANAGER chỉ được quản lý tài khoản ACCOUNTANT và EMPLOYEE",
                    org.springframework.http.HttpStatus.FORBIDDEN, "INSUFFICIENT_PRIVILEGE");
        }
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }
}
