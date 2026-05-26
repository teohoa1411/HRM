package com.hrms.domain.statement.repository;

import com.hrms.domain.statement.entity.StatementHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StatementHistoryRepository extends JpaRepository<StatementHistory, Long> {

    /** Lấy toàn bộ lịch sử của một statement, mới nhất trước */
    List<StatementHistory> findByStatementIdOrderByChangedAtDesc(Long statementId);

    /** Lấy lịch sử gần nhất (1 record) — để biết trạng thái hiện tại từ audit */
    @Query("""
        SELECT sh FROM StatementHistory sh
        WHERE sh.statementId = :statementId
        ORDER BY sh.changedAt DESC
        LIMIT 1
        """)
    java.util.Optional<StatementHistory> findLatestByStatementId(@Param("statementId") Long statementId);

    /** Đếm số lần statement được xuất (chuyển sang EXPORTED) */
    long countByStatementIdAndToStatus(Long statementId, String toStatus);

    /** Kiểm tra xem statement đã từng ở trạng thái SENT chưa */
    boolean existsByStatementIdAndToStatus(Long statementId, String toStatus);
}
