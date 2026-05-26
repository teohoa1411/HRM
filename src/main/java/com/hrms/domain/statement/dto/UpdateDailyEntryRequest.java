package com.hrms.domain.statement.dto;

import java.math.BigDecimal;

public record UpdateDailyEntryRequest(BigDecimal hours, String note) {}
