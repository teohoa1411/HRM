package com.hrms.web.controller;

import com.hrms.domain.account.dto.ChangePasswordRequest;
import com.hrms.domain.account.dto.LoginRequest;
import com.hrms.domain.account.dto.LoginResponse;
import com.hrms.domain.account.dto.RefreshResponse;
import com.hrms.domain.account.service.AuthService;
import com.hrms.domain.account.service.UserService;
import com.hrms.infrastructure.security.SecurityUtils;
import com.hrms.web.advice.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, refresh token, logout")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final UserService userService;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.path:/api/v1/auth}")
    private String cookiePath;

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        LoginResponse loginResponse = authService.login(request, clientIp, userAgent);

        // Set refresh token as HttpOnly cookie
        addRefreshTokenCookie(httpResponse, loginResponse.refreshToken());

        return ResponseEntity.ok(ApiResponse.ok(loginResponse, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh cookie")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = "X-Refresh-Token", required = false) String headerToken,
            HttpServletResponse httpResponse) {

        String token = refreshToken != null ? refreshToken : headerToken;
        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Refresh token missing", "TOKEN_MISSING"));
        }

        RefreshResponse refreshResponse = authService.refresh(token);

        // Set new refresh token as HttpOnly cookie (token rotation)
        addRefreshTokenCookie(httpResponse, refreshResponse.refreshToken());

        return ResponseEntity.ok(ApiResponse.ok(refreshResponse, "Token refreshed"));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout and revoke refresh tokens")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        String username = SecurityUtils.getCurrentUser().username();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        authService.logout(userId, username, companyId);

        // Clear refresh token cookie
        clearRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    @PatchMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change own password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {

        Long userId = SecurityUtils.getCurrentUserId();
        Long companyId = SecurityUtils.getCurrentCompanyId();
        userService.changePassword(userId, companyId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed successfully"));
    }

    // ─── Cookie helpers ───

    private void addRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(cookiePath);
        cookie.setMaxAge(refreshTokenExpiryDays * 24 * 60 * 60); // 7 days in seconds
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(cookiePath);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
