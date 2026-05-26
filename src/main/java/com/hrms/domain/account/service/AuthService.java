package com.hrms.domain.account.service;

import com.hrms.domain.account.dto.LoginRequest;
import com.hrms.domain.account.dto.LoginResponse;
import com.hrms.domain.account.dto.RefreshResponse;
import com.hrms.domain.account.entity.RefreshToken;
import com.hrms.domain.account.entity.User;
import com.hrms.domain.account.repository.RefreshTokenRepository;
import com.hrms.domain.account.repository.UserRepository;
import com.hrms.domain.common.enums.AuditAction;
import com.hrms.domain.common.exception.AccountLockedException;
import com.hrms.domain.common.exception.BusinessException;
import com.hrms.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(request.username())
                .orElseThrow(() -> new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        if (!user.isActive()) {
            auditLogService.log(user.getCompanyId(), user.getId(), user.getUsername(),
                    AuditAction.LOGIN, "User", user.getId(), null, null,
                    "Account disabled", clientIp, userAgent, "FAILED");
            throw new BusinessException("Account is disabled", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        if (user.isLocked()) {
            auditLogService.log(user.getCompanyId(), user.getId(), user.getUsername(),
                    AuditAction.LOGIN, "User", user.getId(), null, null,
                    "Account locked", clientIp, userAgent, "FAILED");
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, clientIp, userAgent);
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        user.resetFailedLoginCount();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Log successful login
        auditLogService.log(user.getCompanyId(), user.getId(), user.getUsername(),
                AuditAction.LOGIN, "User", user.getId(), null, null,
                null, clientIp, userAgent, "SUCCESS");

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = createRefreshToken(user, clientIp, userAgent);

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCompanyId(),
                user.isMustChangePassword()
        );
    }

    @Transactional
    public RefreshResponse refresh(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));

        if (!refreshToken.isValid()) {
            throw new BusinessException("Refresh token expired or revoked", HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
        }

        User user = refreshToken.getUser();
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        String newRawRefreshToken = createRefreshToken(user, refreshToken.getCreatedIp(), refreshToken.getUserAgent());
        String newAccessToken = jwtService.generateAccessToken(user);

        return new RefreshResponse(
                newAccessToken,
                newRawRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCompanyId(),
                user.isMustChangePassword()
        );
    }

    @Transactional
    public void logout(Long userId, String username, Long companyId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());

        // Log logout action
        auditLogService.log(companyId, userId, username,
                AuditAction.LOGOUT, "User", userId, null, null,
                null, null, null, "SUCCESS");
    }

    private void handleFailedLogin(User user, String clientIp, String userAgent) {
        user.incrementFailedLoginCount();
        String description = "Invalid password attempt (" + user.getFailedLoginCount() + "/" + MAX_LOGIN_ATTEMPTS + ")";

        if (user.getFailedLoginCount() >= MAX_LOGIN_ATTEMPTS) {
            user.lock(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            description = "Account locked after " + MAX_LOGIN_ATTEMPTS + " failed attempts";
            log.warn("Account locked for user: {}", user.getUsername());
            auditLogService.log(user.getCompanyId(), user.getId(), user.getUsername(),
                    AuditAction.LOGIN, "User", user.getId(), null, null,
                    description, clientIp, userAgent, "FAILED");
        } else {
            auditLogService.log(user.getCompanyId(), user.getId(), user.getUsername(),
                    AuditAction.LOGIN, "User", user.getId(), null, null,
                    description, clientIp, userAgent, "FAILED");
        }

        userRepository.save(user);
    }

    private String createRefreshToken(User user, String clientIp, String userAgent) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .createdIp(clientIp)
                .userAgent(userAgent)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
