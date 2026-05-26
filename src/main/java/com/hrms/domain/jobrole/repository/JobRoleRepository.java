package com.hrms.domain.jobrole.repository;

import com.hrms.domain.jobrole.entity.JobRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRoleRepository extends JpaRepository<JobRole, Long> {

    /** Danh sách tất cả job roles của công ty (phân trang) */
    Page<JobRole> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    /** Danh sách job roles đang active của công ty (dùng cho dropdown) */
    List<JobRole> findByCompanyIdAndActiveAndDeletedAtIsNull(Long companyId, boolean active);

    /** Lấy job role theo id và kiểm tra thuộc công ty */
    Optional<JobRole> findByCompanyIdAndIdAndDeletedAtIsNull(Long companyId, Long id);

    /** Kiểm tra code đã tồn tại trong công ty chưa */
    boolean existsByCompanyIdAndCodeAndDeletedAtIsNull(Long companyId, String code);

    /** Lấy theo id (không check company — dùng nội bộ khi đã biết companyId) */
    Optional<JobRole> findByIdAndDeletedAtIsNull(Long id);
}
