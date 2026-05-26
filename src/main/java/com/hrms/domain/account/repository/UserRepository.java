package com.hrms.domain.account.repository;

import com.hrms.domain.account.entity.User;
import com.hrms.domain.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Page<User> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Page<User> findByRoleAndDeletedAtIsNull(Role role, Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.companyId = :companyId")
    void deactivateAllByCompanyId(@Param("companyId") Long companyId);
}
