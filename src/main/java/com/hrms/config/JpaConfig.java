package com.hrms.config;

import com.hrms.infrastructure.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Optional;

@Configuration
@EnableJpaRepositories(basePackages = "com.hrms.domain")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            try {
                return Optional.ofNullable(SecurityUtils.getCurrentUserId());
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }
}
