package com.hrms.web.filter;

import com.hrms.domain.common.enums.Role;
import com.hrms.infrastructure.security.HrmsUserPrincipal;
import com.hrms.infrastructure.security.JwtService;
import com.hrms.infrastructure.security.TenantContextHolder;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Long userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);
            Long companyId = jwtService.extractCompanyId(token);
            Role role = jwtService.extractRole(token);
            String email = jwtService.validateAndExtractClaims(token).get("email", String.class);

            HrmsUserPrincipal principal = new HrmsUserPrincipal(userId, username, email, role, companyId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            TenantContextHolder.setCurrentTenant(companyId);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Try to extract username from expired token
            String username = "UNKNOWN";
            try {
                username = (String) e.getClaims().get("username");
                if (username == null) {
                    username = "UNKNOWN";
                }
            } catch (Exception ex) {
                // Unable to extract username from token
            }
            log.warn("Expired JWT token - user={} from {}: {}",
                    username, request.getRemoteAddr(), e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid JWT token from {} accessing {}: {}",
                    request.getRemoteAddr(), request.getRequestURI(), e.getMessage());
        } finally {
            filterChain.doFilter(request, response);
        }
    }
}
