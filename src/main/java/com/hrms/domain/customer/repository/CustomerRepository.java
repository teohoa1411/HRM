package com.hrms.domain.customer.repository;

import com.hrms.domain.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    List<Customer> findByCompanyIdAndActiveAndDeletedAtIsNull(Long companyId, boolean active);

    Optional<Customer> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByCodeAndCompanyIdAndDeletedAtIsNull(String code, Long companyId);
}
