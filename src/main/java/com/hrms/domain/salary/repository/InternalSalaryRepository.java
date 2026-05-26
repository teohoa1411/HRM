package com.hrms.domain.salary.repository;

import com.hrms.domain.salary.entity.InternalSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InternalSalaryRepository extends JpaRepository<InternalSalary, Long> {

    List<InternalSalary> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    @Query("SELECT s FROM InternalSalary s WHERE s.employeeId = :employeeId AND s.effectiveTo IS NULL")
    Optional<InternalSalary> findCurrentSalary(Long employeeId);

    @Modifying
    @Query("UPDATE InternalSalary s SET s.effectiveTo = :closedDate WHERE s.employeeId = :employeeId AND s.effectiveTo IS NULL")
    void closeCurrentSalary(Long employeeId, LocalDate closedDate);

    @Query("""
        SELECT s FROM InternalSalary s
        WHERE s.employeeId = :employeeId
          AND s.effectiveFrom <= :refDate
          AND (s.effectiveTo IS NULL OR s.effectiveTo >= :refDate)
        ORDER BY s.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<InternalSalary> findSalaryAt(Long employeeId, LocalDate refDate);
}
