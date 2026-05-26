package com.hrms.domain.report.service;

import com.hrms.domain.employee.repository.EmployeeRepository;
import com.hrms.domain.report.dto.CostReportResponse;
import com.hrms.domain.report.dto.RevenueReportItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final JdbcTemplate jdbcTemplate;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public CostReportResponse getCostReport(Long companyId, String fromMonth, String toMonth) {
        String sql = """
                SELECT
                    COALESCE(SUM(cost_amount), 0)  AS total_cost,
                    COALESCE(SUM(sell_amount), 0)  AS total_revenue,
                    COUNT(DISTINCT employee_id)    AS employee_count
                FROM billing_records
                WHERE company_id = ?
                  AND billing_month >= ?
                  AND billing_month <= ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            BigDecimal totalCost = rs.getBigDecimal("total_cost");
            BigDecimal totalRevenue = rs.getBigDecimal("total_revenue");
            BigDecimal profit = totalRevenue.subtract(totalCost);
            return new CostReportResponse(
                    fromMonth,
                    toMonth,
                    totalCost,
                    totalRevenue,
                    profit,
                    rs.getLong("employee_count"),
                    "VND"
            );
        }, companyId, fromMonth, toMonth);
    }

    @Transactional(readOnly = true)
    public List<RevenueReportItem> getRevenueByJobRole(Long companyId, String fromMonth, String toMonth) {
        String sql = """
                SELECT
                    br.job_role_id,
                    jr.name             AS job_role_name,
                    COUNT(DISTINCT br.employee_id) AS headcount,
                    COALESCE(SUM(br.cost_amount), 0)  AS cost,
                    COALESCE(SUM(br.sell_amount), 0)  AS revenue,
                    COALESCE(SUM(br.sell_amount) - SUM(br.cost_amount), 0) AS margin
                FROM billing_records br
                JOIN job_roles jr ON jr.id = br.job_role_id
                WHERE br.company_id = ?
                  AND br.billing_month >= ?
                  AND br.billing_month <= ?
                GROUP BY br.job_role_id, jr.name
                ORDER BY revenue DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RevenueReportItem(
                rs.getLong("job_role_id"),
                rs.getString("job_role_name"),
                rs.getLong("headcount"),
                rs.getBigDecimal("cost"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("margin")
        ), companyId, fromMonth, toMonth);
    }

    @Transactional(readOnly = true)
    public List<RevenueReportItem> getRevenueBySourceType(Long companyId, String fromMonth, String toMonth) {
        String sql = """
                SELECT
                    0                   AS job_role_id,
                    br.source_type      AS job_role_name,
                    COUNT(DISTINCT br.employee_id) AS headcount,
                    COALESCE(SUM(br.cost_amount), 0)  AS cost,
                    COALESCE(SUM(br.sell_amount), 0)  AS revenue,
                    COALESCE(SUM(br.sell_amount) - SUM(br.cost_amount), 0) AS margin
                FROM billing_records br
                WHERE br.company_id = ?
                  AND br.billing_month >= ?
                  AND br.billing_month <= ?
                GROUP BY br.source_type
                ORDER BY cost DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RevenueReportItem(
                rs.getLong("job_role_id"),
                rs.getString("job_role_name"),
                rs.getLong("headcount"),
                rs.getBigDecimal("cost"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("margin")
        ), companyId, fromMonth, toMonth);
    }

    @Transactional(readOnly = true)
    public List<RevenueReportItem> getRevenueByDepartment(Long companyId, String fromMonth, String toMonth) {
        String sql = """
                SELECT
                    COALESCE(d.id, 0)        AS job_role_id,
                    COALESCE(d.name, 'Chưa phân bộ phận') AS job_role_name,
                    COUNT(DISTINCT br.employee_id) AS headcount,
                    COALESCE(SUM(br.cost_amount), 0)  AS cost,
                    COALESCE(SUM(br.sell_amount), 0)  AS revenue,
                    COALESCE(SUM(br.sell_amount) - SUM(br.cost_amount), 0) AS margin
                FROM billing_records br
                JOIN employees e ON e.id = br.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                WHERE br.company_id = ?
                  AND br.billing_month >= ?
                  AND br.billing_month <= ?
                GROUP BY d.id, d.name
                ORDER BY cost DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RevenueReportItem(
                rs.getLong("job_role_id"),
                rs.getString("job_role_name"),
                rs.getLong("headcount"),
                rs.getBigDecimal("cost"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("margin")
        ), companyId, fromMonth, toMonth);
    }

    @Transactional(readOnly = true)
    public List<RevenueReportItem> getRevenueByCustomer(Long companyId, String fromMonth, String toMonth) {
        String sql = """
                SELECT
                    COALESCE(c.id, 0)        AS job_role_id,
                    COALESCE(c.name, 'Chưa phân KH') AS job_role_name,
                    COUNT(DISTINCT br.employee_id) AS headcount,
                    COALESCE(SUM(br.cost_amount), 0)  AS cost,
                    COALESCE(SUM(br.sell_amount), 0)  AS revenue,
                    COALESCE(SUM(br.sell_amount) - SUM(br.cost_amount), 0) AS margin
                FROM billing_records br
                LEFT JOIN customers c ON c.id = br.customer_id
                WHERE br.company_id = ?
                  AND br.billing_month >= ?
                  AND br.billing_month <= ?
                GROUP BY c.id, c.name
                ORDER BY revenue DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RevenueReportItem(
                rs.getLong("job_role_id"),
                rs.getString("job_role_name"),
                rs.getLong("headcount"),
                rs.getBigDecimal("cost"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("margin")
        ), companyId, fromMonth, toMonth);
    }

    @Transactional(readOnly = true)
    public long countActiveEmployees(Long companyId) {
        return employeeRepository.countActiveByCompanyId(companyId);
    }
}
